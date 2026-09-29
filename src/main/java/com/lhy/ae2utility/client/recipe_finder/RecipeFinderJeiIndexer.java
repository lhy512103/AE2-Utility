package com.lhy.ae2utility.client.recipe_finder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;

import com.lhy.ae2utility.jei.EncodePatternButtonState;
import com.lhy.ae2utility.jei.JeiEncodePacketFactory;
import com.lhy.ae2utility.jei.JeiRecipePageKinds;
import com.lhy.ae2utility.jei.JeiRecipesBatchEncode;
import com.lhy.ae2utility.recipe_finder.RecipeFinderFeatureClassifier;
import com.lhy.ae2utility.util.GenericIngredientUtil;

/** 遍历 JEI 全部分类，为配方查找器建立候选索引。 */
public final class RecipeFinderJeiIndexer {
    private RecipeFinderJeiIndexer() {
    }

    public static BuildResult build() {
        IJeiRuntime runtime = EncodePatternButtonState.getJeiRuntime();
        if (runtime == null) {
            return new BuildResult(List.of(), Component.translatable("gui.ae2utility.recipe_finder.jei_unavailable"));
        }

        IFocusGroup emptyFocus = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        Map<String, RecipeFinderCandidate> unique = new LinkedHashMap<>();
        List<RecipeType<?>> recipeTypes = new ArrayList<>(runtime.getJeiHelpers().getAllRecipeTypes().toList());
        recipeTypes.sort(Comparator.comparing(type -> type.getUid().toString()));
        for (RecipeType<?> recipeType : recipeTypes) {
            collectType(runtime, recipeType, emptyFocus, unique);
        }

        List<RecipeFinderCandidate> recipes = unique.values().stream()
                .sorted(Comparator.comparing(RecipeFinderCandidate::sourceModName)
                        .thenComparing(RecipeFinderCandidate::machineLabel)
                        .thenComparing(RecipeFinderCandidate::displayName))
                .toList();

        Component status = recipes.isEmpty()
                ? Component.translatable("gui.ae2utility.recipe_finder.index_empty")
                : Component.translatable("gui.ae2utility.recipe_finder.indexed", recipes.size());
        return new BuildResult(recipes, status);
    }

    /** 重新为所选候选创建 JEI 布局，走与 JEI 批量编码相同的流程。 */
    public static void encode(List<RecipeFinderCandidate> selected, boolean shiftUpload) {
        IJeiRuntime runtime = EncodePatternButtonState.getJeiRuntime();
        if (runtime == null || selected.isEmpty()) {
            return;
        }
        IFocusGroup emptyFocus = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        List<IRecipeLayoutDrawable<?>> layouts = new ArrayList<>(selected.size());
        for (RecipeFinderCandidate candidate : selected) {
            createLayout(runtime, candidate.category(), candidate.recipe(), emptyFocus).ifPresent(layouts::add);
        }
        JeiRecipesBatchEncode.run(layouts, shiftUpload);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Optional<IRecipeLayoutDrawable<?>> createLayout(IJeiRuntime runtime, IRecipeCategory<?> category,
            Object recipe, IFocusGroup focusGroup) {
        return (Optional) runtime.getRecipeManager().createRecipeLayoutDrawable((IRecipeCategory) category, recipe,
                focusGroup);
    }

    private static <T> void collectType(IJeiRuntime runtime, RecipeType<T> recipeType, IFocusGroup emptyFocus,
            Map<String, RecipeFinderCandidate> unique) {
        IRecipeCategory<T> category;
        List<T> recipes;
        try {
            category = runtime.getRecipeManager().getRecipeCategory(recipeType);
            recipes = runtime.getRecipeManager().createRecipeLookup(recipeType).get().toList();
        } catch (RuntimeException e) {
            return;
        }
        if (category == null) {
            return;
        }
        for (T recipe : recipes) {
            try {
                createCandidate(runtime, category, recipe, emptyFocus)
                        .ifPresent(view -> unique.putIfAbsent(view.identityKey(), view));
            } catch (RuntimeException ignored) {
                // 第三方分类构建布局时可能抛异常，跳过该配方即可
            }
        }
    }

    private static <T> Optional<RecipeFinderCandidate> createCandidate(IJeiRuntime runtime, IRecipeCategory<T> category,
            T recipe, IFocusGroup emptyFocus) {
        IRecipeLayoutDrawable<T> layout = runtime.getRecipeManager()
                .createRecipeLayoutDrawable(category, recipe, emptyFocus)
                .orElse(null);
        if (layout == null || JeiRecipePageKinds.isTagRecipeLayout(layout)) {
            return Optional.empty();
        }

        IRecipeSlotsView slotsView = layout.getRecipeSlotsView();
        IIngredientManager ingredientManager = runtime.getIngredientManager();
        List<IRecipeSlotView> inputSlots = slotsView.getSlotViews(RecipeIngredientRole.INPUT);
        List<IRecipeSlotView> outputSlots = slotsView.getSlotViews(RecipeIngredientRole.OUTPUT);
        DisplayedIngredient primaryOutput = firstDisplayed(outputSlots, ingredientManager);
        if (primaryOutput == null) {
            return Optional.empty();
        }

        ResourceLocation recipeId = category.getRegistryName(recipe);
        String sourceModId = resolveSourceModId(recipeId, primaryOutput);
        String machineLabel = category.getTitle().getString();
        Set<String> outputItemIds = collectItemIds(outputSlots);
        Set<String> inputItemIds = collectItemIds(inputSlots);
        boolean encodable = JeiRecipePageKinds.isEncodableRecipeLayout(layout)
                && JeiEncodePacketFactory.tryCreate(layout, false).isPresent();
        String identityKey = signatureOf(recipeId, category.getRecipeType().getUid().toString(),
                primaryOutput.displayName(), collectSlotNames(inputSlots, ingredientManager),
                collectSlotNames(outputSlots.subList(1, outputSlots.size()), ingredientManager));

        return Optional.of(new RecipeFinderCandidate(
                identityKey,
                primaryOutput.previewStack(),
                primaryOutput.displayName(),
                sourceModId,
                RecipeFinderFeatureClassifier.modDisplayName(sourceModId),
                machineLabel,
                outputItemIds,
                inputItemIds,
                collectModIds(outputItemIds, inputItemIds),
                collectFeatures(inputSlots),
                collectFeatures(outputSlots),
                encodable,
                category,
                recipe));
    }

    private static Set<String> collectFeatures(List<IRecipeSlotView> slots) {
        Set<String> features = new LinkedHashSet<>();
        for (IRecipeSlotView slot : slots) {
            slot.getAllIngredients().forEach(ingredient ->
                    features.addAll(RecipeFinderFeatureClassifier.classifyIngredient(ingredient.getIngredient())));
        }
        if (features.isEmpty()) {
            features.add("other");
        }
        return Set.copyOf(features);
    }

    private static Set<String> collectItemIds(List<IRecipeSlotView> slots) {
        Set<String> ids = new LinkedHashSet<>();
        for (IRecipeSlotView slot : slots) {
            slot.getItemStacks()
                    .filter(stack -> !stack.isEmpty())
                    .forEach(stack -> ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
        }
        return Set.copyOf(ids);
    }

    private static Set<String> collectModIds(Set<String> outputItemIds, Set<String> inputItemIds) {
        Set<String> mods = new LinkedHashSet<>();
        for (Set<String> ids : List.of(outputItemIds, inputItemIds)) {
            for (String id : ids) {
                int i = id.indexOf(':');
                if (i > 0) {
                    mods.add(id.substring(0, i));
                }
            }
        }
        return Set.copyOf(mods);
    }

    private static List<String> collectSlotNames(List<IRecipeSlotView> slots, IIngredientManager ingredientManager) {
        List<String> names = new ArrayList<>();
        for (IRecipeSlotView slot : slots) {
            DisplayedIngredient displayed = getDisplayed(slot, ingredientManager);
            if (displayed != null) {
                names.add(displayed.displayName());
            }
        }
        return names;
    }

    private static @Nullable DisplayedIngredient firstDisplayed(List<IRecipeSlotView> slots,
            IIngredientManager ingredientManager) {
        for (IRecipeSlotView slot : slots) {
            DisplayedIngredient displayed = getDisplayed(slot, ingredientManager);
            if (displayed != null) {
                return displayed;
            }
        }
        return null;
    }

    private static @Nullable DisplayedIngredient getDisplayed(IRecipeSlotView slot, IIngredientManager ingredientManager) {
        ITypedIngredient<?> ingredient = slot.getDisplayedIngredient()
                .orElseGet(() -> slot.getAllIngredients().findFirst().orElse(null));
        if (ingredient == null) {
            return null;
        }
        ItemStack previewStack = ingredient.getItemStack()
                .map(ItemStack::copy)
                .orElseGet(() -> wrapIngredientPreview(ingredient.getIngredient()));
        return new DisplayedIngredient(displayName(ingredientManager, ingredient), previewStack);
    }

    private static <T> String displayName(IIngredientManager ingredientManager, ITypedIngredient<T> ingredient) {
        return ingredientManager.getIngredientHelper(ingredient.getType()).getDisplayName(ingredient.getIngredient());
    }

    private static ItemStack wrapIngredientPreview(Object ingredient) {
        GenericStack generic = GenericIngredientUtil.toGenericStack(ingredient, 1);
        if (generic == null) {
            return ItemStack.EMPTY;
        }
        if (generic.what() instanceof AEItemKey itemKey) {
            return itemKey.toStack((int) Math.max(1, generic.amount()));
        }
        return GenericStack.wrapInItemStack(generic);
    }

    private static String resolveSourceModId(@Nullable ResourceLocation recipeId, DisplayedIngredient primaryOutput) {
        if (recipeId != null) {
            return recipeId.getNamespace();
        }
        if (!primaryOutput.previewStack().isEmpty()) {
            return BuiltInRegistries.ITEM.getKey(primaryOutput.previewStack().getItem()).getNamespace();
        }
        return "unknown";
    }

    private static String signatureOf(@Nullable ResourceLocation recipeId, String machineKey, String displayName,
            List<String> inputNames, List<String> extraOutputNames) {
        StringBuilder builder = new StringBuilder();
        if (recipeId != null) {
            builder.append(recipeId);
        } else {
            builder.append(machineKey).append('#').append(displayName);
        }
        for (String inputName : inputNames) {
            builder.append('|').append(inputName);
        }
        for (String outputName : extraOutputNames) {
            builder.append('|').append(outputName);
        }
        return builder.toString();
    }

    public record BuildResult(List<RecipeFinderCandidate> recipes, Component status) {
    }

    private record DisplayedIngredient(String displayName, ItemStack previewStack) {
    }
}
