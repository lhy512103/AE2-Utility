package com.lhy.ae2utility.jei;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import com.lhy.ae2utility.network.RecipeTransferPacketHelper;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;

import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IGuiHelper;

import mezz.jei.api.recipe.IRecipeManager;

import com.mojang.serialization.Codec;

import com.lhy.ae2utility.Ae2UtilityMod;

/**
 * 通过反射操作 JEI 内部 {@code BookmarkList}，与 {@link com.lhy.ae2utility.network.RecipeTransferPacketHelper#getBookmarkKeys()} 一致。
 */
public final class JeiBookmarkUtil {
    private JeiBookmarkUtil() {}

    /** 是否为 Ctrl+Shift+鼠标左键（含 JEI 预检 {@code isSimulate} 阶段）。 */
    public static boolean isCtrlShiftLeftClickAnchor(IJeiUserInput input) {
        if (!Screen.hasControlDown() || !Screen.hasShiftDown()) {
            return false;
        }
        InputConstants.Key key = input.getKey();
        return key.getType() == InputConstants.Type.MOUSE && key.getValue() == GLFW.GLFW_MOUSE_BUTTON_LEFT;
    }

    /** Ctrl+左键（不按 Shift）：与 {@link #isCtrlShiftLeftClickAnchor} 互斥。 */
    public static boolean isCtrlLeftClickAnchor(IJeiUserInput input) {
        if (!Screen.hasControlDown() || Screen.hasShiftDown()) {
            return false;
        }
        InputConstants.Key key = input.getKey();
        return key.getType() == InputConstants.Type.MOUSE && key.getValue() == GLFW.GLFW_MOUSE_BUTTON_LEFT;
    }

    /**
     * 将「当前 ME 无自动合成样板」的输入槽加入 JEI 收藏栏；已存在收藏中的会跳过。
     * 多选一槽位始终收藏 {@link IRecipeSlotView#getAllIngredients()} 的<strong>静态顺序第一个</strong>（与 JEI 注册顺序一致，例如锻造台木板为橡木木板），不随轮转显示变化。
     *
     * @return 新加入收藏的数量
     */
    public static int bookmarkMissingCraftingInputs(IRecipeSlotsView slotsView) {
        IJeiRuntime runtime = Ae2UtilityJeiPlugin.getJeiRuntime();
        if (runtime == null) {
            return 0;
        }
        IBookmarkOverlay overlay = runtime.getBookmarkOverlay();
        if (overlay == null) {
            return 0;
        }
        try {
            Field bookmarkListField = overlay.getClass().getDeclaredField("bookmarkList");
            bookmarkListField.setAccessible(true);
            Object bookmarkList = bookmarkListField.get(overlay);
            Field factoryField = bookmarkList.getClass().getDeclaredField("bookmarkFactory");
            factoryField.setAccessible(true);
            Object bookmarkFactory = factoryField.get(bookmarkList);
            Method create = bookmarkFactory.getClass().getMethod("create", ITypedIngredient.class);
            Class<?> iBookmarkClass = Class.forName("mezz.jei.gui.bookmarks.IBookmark");
            Method add = bookmarkList.getClass().getMethod("add", iBookmarkClass);

            int added = 0;
            for (IRecipeSlotView slotView : slotsView.getSlotViews(RecipeIngredientRole.INPUT)) {
                if (slotView.isEmpty()) {
                    continue;
                }
                GenericStack one = RecipeTransferPacketHelper.genericStackForCraftableHighlightInputSlot(slotView);
                boolean anyCraftable =
                        one != null && one.what() != null && CraftableStateCache.isCraftable(one.what());
                if (anyCraftable) {
                    continue;
                }
                List<ITypedIngredient<?>> staticOrder = slotView.getAllIngredients().toList();
                if (staticOrder.isEmpty()) {
                    continue;
                }
                ITypedIngredient<?> toBookmark = staticOrder.getFirst();
                Object bookmark = create.invoke(bookmarkFactory, toBookmark);
                if (Boolean.TRUE.equals(add.invoke(bookmarkList, bookmark))) {
                    added++;
                }
            }
            return added;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static boolean bookmarkRecipe(ResourceLocation recipeId, GenericStack primaryOutput) {
        if (recipeId == null || primaryOutput == null || primaryOutput.what() == null) {
            return false;
        }
        IJeiRuntime runtime = Ae2UtilityJeiPlugin.getJeiRuntime();
        if (runtime == null) {
            return false;
        }
        IBookmarkOverlay overlay = runtime.getBookmarkOverlay();
        if (overlay == null) {
            return false;
        }

        try {
            Object bookmarkList = getBookmarkList(overlay);
            if (bookmarkList == null) {
                return false;
            }

            ITypedIngredient<?> displayIngredient = toTypedIngredient(runtime.getIngredientManager(), primaryOutput);
            if (displayIngredient == null) {
                return false;
            }

            Object bookmark = createRecipeBookmark(runtime, recipeId, displayIngredient);
            if (bookmark == null) {
                return false;
            }

            Class<?> iBookmarkClass = Class.forName("mezz.jei.gui.bookmarks.IBookmark");
            Method add = bookmarkList.getClass().getMethod("add", iBookmarkClass);
            return Boolean.TRUE.equals(add.invoke(bookmarkList, bookmark));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 清空全部 JEI 收藏（写入空列表并与 JEI 书签文件同步）。
     *
     * @return 清空前书签数量；JEI 未就绪或结构与当前版本不兼容时返回 {@code -1}
     */
    @SuppressWarnings("unchecked")
    public static int clearAllBookmarks() {
        IJeiRuntime runtime = Ae2UtilityJeiPlugin.getJeiRuntime();
        if (runtime == null) {
            return -1;
        }
        IBookmarkOverlay overlay = runtime.getBookmarkOverlay();
        if (overlay == null) {
            return -1;
        }
        try {
            Object bookmarkList = getBookmarkList(overlay);
            if (bookmarkList == null) {
                return -1;
            }
            Field listField = bookmarkList.getClass().getDeclaredField("bookmarksList");
            Field setField = bookmarkList.getClass().getDeclaredField("bookmarksSet");
            listField.setAccessible(true);
            setField.setAccessible(true);
            List<Object> bookmarksList = (List<Object>) listField.get(bookmarkList);
            int count = bookmarksList.size();
            bookmarksList.clear();
            Set<Object> bookmarksSet = (Set<Object>) setField.get(bookmarkList);
            bookmarksSet.clear();

            Method notify = bookmarkList.getClass().getDeclaredMethod("notifyListenersOfChange");
            notify.setAccessible(true);
            notify.invoke(bookmarkList);

            Object bookmarkConfig = readDeclaredField(bookmarkList, "bookmarkConfig");
            Method saveBookmarks = bookmarkConfig.getClass().getMethod(
                    "saveBookmarks",
                    IRecipeManager.class,
                    IFocusFactory.class,
                    IGuiHelper.class,
                    IIngredientManager.class,
                    RegistryAccess.class,
                    ICodecHelper.class,
                    List.class,
                    Codec.class);
            saveBookmarks.invoke(
                    bookmarkConfig,
                    readDeclaredField(bookmarkList, "recipeManager"),
                    readDeclaredField(bookmarkList, "focusFactory"),
                    readDeclaredField(bookmarkList, "guiHelper"),
                    readDeclaredField(bookmarkList, "ingredientManager"),
                    readDeclaredField(bookmarkList, "registryAccess"),
                    readDeclaredField(bookmarkList, "codecHelper"),
                    bookmarksList,
                    readDeclaredField(bookmarkList, "bookmarkCodec"));

            JeiBookmarkKeysCache.invalidate();
            return count;
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            Ae2UtilityMod.LOGGER.warn("clearAllBookmarks failed: {}", c != null ? c : e);
            return -1;
        } catch (Throwable t) {
            Ae2UtilityMod.LOGGER.warn("clearAllBookmarks failed: {}", String.valueOf(t));
            return -1;
        }
    }

    private static Object readDeclaredField(Object target, String name)
            throws NoSuchFieldException, IllegalAccessException {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static AEKey keyFromTyped(ITypedIngredient<?> typed) {
        Object ing = typed.getIngredient();
        if (ing instanceof net.minecraft.world.item.ItemStack is && !is.isEmpty()) {
            return AEItemKey.of(is);
        }
        if (ing instanceof net.neoforged.neoforge.fluids.FluidStack fs && !fs.isEmpty()) {
            return AEFluidKey.of(fs);
        }
        return null;
    }

    private static Object getBookmarkList(IBookmarkOverlay overlay) throws ReflectiveOperationException {
        Field bookmarkListField = overlay.getClass().getDeclaredField("bookmarkList");
        bookmarkListField.setAccessible(true);
        return bookmarkListField.get(overlay);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Object createRecipeBookmark(IJeiRuntime runtime, ResourceLocation recipeId, ITypedIngredient<?> displayIngredient)
            throws ReflectiveOperationException {
        IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
        IFocus<?> focus = focusFactory.createFocus(RecipeIngredientRole.OUTPUT, displayIngredient);
        List<IRecipeCategory<?>> categories = runtime.getRecipeManager().createRecipeCategoryLookup()
                .limitFocus(List.of(focus))
                .includeHidden()
                .get()
                .toList();
        for (IRecipeCategory<?> category : categories) {
            RecipeType<?> recipeType = category.getRecipeType();
            Object recipe = findRecipeById(runtime, category, recipeType, recipeId, focus);
            if (recipe == null) {
                continue;
            }

            Class<?> recipeBookmarkClass = Class.forName("mezz.jei.gui.bookmarks.RecipeBookmark");
            return recipeBookmarkClass
                    .getConstructor(IRecipeCategory.class, Object.class, ResourceLocation.class, ITypedIngredient.class, boolean.class)
                    .newInstance(category, recipe, recipeId, displayIngredient, true);
        }
        return null;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Object findRecipeById(IJeiRuntime runtime, IRecipeCategory<?> category, RecipeType<?> recipeType,
            ResourceLocation recipeId, IFocus<?> focus) {
        try {
            List<?> recipeList = runtime.getRecipeManager().createRecipeLookup((RecipeType<Object>) recipeType)
                    .includeHidden()
                    .limitFocus(List.of((IFocus<Object>) focus))
                    .get()
                    .toList();
            for (Object recipe : recipeList) {
                ResourceLocation candidateId = ((IRecipeCategory) category).getRegistryName(recipe);
                if (recipeId.equals(candidateId)) {
                    return recipe;
                }
            }
        } catch (ClassCastException ignored) {
        }
        return null;
    }

    private static ITypedIngredient<?> toTypedIngredient(IIngredientManager ingredientManager, GenericStack stack) {
        Object ingredient = null;
        if (stack.what() instanceof AEItemKey itemKey) {
            ingredient = itemKey.toStack((int) Math.max(1L, Math.min(Integer.MAX_VALUE, stack.amount())));
        } else if (stack.what() instanceof AEFluidKey fluidKey) {
            ingredient = fluidKey.toStack((int) Math.max(1L, Math.min(Integer.MAX_VALUE, stack.amount())));
        } else if (stack.what() != null) {
            ingredient = stack.what().wrapForDisplayOrFilter();
        }
        if (ingredient == null) {
            return null;
        }
        Optional<? extends ITypedIngredient<?>> typed = ingredientManager.createTypedIngredient(ingredient, true);
        return typed.orElse(null);
    }
}
