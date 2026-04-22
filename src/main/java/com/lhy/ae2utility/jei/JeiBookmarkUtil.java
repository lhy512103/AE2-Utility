package com.lhy.ae2utility.jei;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.Screen;

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
                boolean anyCraftable = false;
                for (ITypedIngredient<?> typed : slotView.getAllIngredients().toList()) {
                    AEKey key = keyFromTyped(typed);
                    if (key != null && CraftableStateCache.isCraftable(key)) {
                        anyCraftable = true;
                        break;
                    }
                }
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
}
