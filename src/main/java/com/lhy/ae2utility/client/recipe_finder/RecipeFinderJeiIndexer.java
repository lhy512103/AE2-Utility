package com.lhy.ae2utility.client.recipe_finder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;

import com.lhy.ae2utility.jei.Ae2UtilityJeiPlugin;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.RecipeTransferPacketHelper;
import com.lhy.ae2utility.recipe_finder.RecipeFinderCandidateView;
import com.lhy.ae2utility.recipe_finder.RecipeFinderFeatureClassifier;
import com.lhy.ae2utility.util.GenericIngredientUtil;

public final class RecipeFinderJeiIndexer {
    private RecipeFinderJeiIndexer() {
    }

    public static BuildResult build() {
        IJeiRuntime runtime = Ae2UtilityJeiPlugin.getJeiRuntime();
        if (runtime == null) {
            return new BuildResult(List.of(), "JEI 未就绪，无法读取全局配方。");
        }

        Map<String, RecipeFinderCandidateView> unique = new LinkedHashMap<>();
        for (RecipeType<?> recipeType : getOrderedRecipeTypes(runtime)) {
            collectType(runtime, recipeType, unique);
        }

        List<RecipeFinderCandidateView> recipes = unique.values().stream()
                .sorted(Comparator.comparing(RecipeFinderCandidateView::sourceModName)
                        .thenComparing(RecipeFinderCandidateView::machineLabel)
                        .thenComparing(RecipeFinderCandidateView::displayName))
                .toList();

        String status = recipes.isEmpty()
                ? "没有从 JEI 中读取到可用配方。"
                : "已索引 " + recipes.size() + " 条 JEI 配方。";
        return new BuildResult(recipes, status);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void collectType(IJeiRuntime runtime, RecipeType<?> recipeType,
            Map<String, RecipeFinderCandidateView> unique) {
        IRecipeCategory category = runtime.getRecipeManager().getRecipeCategory((RecipeType) recipeType);
        if (category == null) {
            return;
        }

        List<?> recipes = runtime.getRecipeManager()
                .createRecipeLookup((RecipeType) recipeType)
                .get()
                .toList();

        for (Object recipe : recipes) {
            java.util.Optional<RecipeFinderCandidateView> candidate = createCandidate(runtime, recipeType, category, recipe);
            candidate.ifPresent(view -> unique.putIfAbsent(view.identityKey(), view));
        }
    }

    private static <T> java.util.Optional<RecipeFinderCandidateView> createCandidate(IJeiRuntime runtime,
            RecipeType<T> recipeType, IRecipeCategory<T> category, Object rawRecipe) {
        T recipe;
        try {
            @SuppressWarnings("unchecked")
            T cast = (T) rawRecipe;
            recipe = cast;
        } catch (ClassCastException ex) {
            return java.util.Optional.empty();
        }

        IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
        var layout = runtime.getRecipeManager()
                .createRecipeLayoutDrawable(category, recipe, focusFactory.createFocusGroup(List.of()))
                .orElse(null);
        if (layout == null) {
            return java.util.Optional.empty();
        }

        IRecipeSlotsView slotsView = layout.getRecipeSlotsView();
        IIngredientManager ingredientManager = runtime.getIngredientManager();
        List<IRecipeSlotView> outputSlots = slotsView.getSlotViews(RecipeIngredientRole.OUTPUT);
        if (outputSlots.isEmpty()) {
            return java.util.Optional.empty();
        }

        DisplayedIngredient primaryOutput = firstDisplayed(outputSlots, ingredientManager);
        if (primaryOutput == null) {
            return java.util.Optional.empty();
        }

        ResourceLocation recipeId = category.getRegistryName(recipe);
        String sourceModId = resolveSourceModId(recipeId, primaryOutput);
        String sourceModName = RecipeFinderFeatureClassifier.modDisplayName(sourceModId);
        String machineKey = recipeType.getUid().toString();
        String machineLabel = category.getTitle().getString();
        Set<String> outputItemIds = collectItemIds(outputSlots);
        Set<String> inputItemIds = collectItemIds(slotsView.getSlotViews(RecipeIngredientRole.INPUT));
        Set<String> involvedModIds = collectModIds(outputItemIds, inputItemIds);
        Set<String> inputFeatures = collectFeatures(slotsView.getSlotViews(RecipeIngredientRole.INPUT));
        Set<String> outputFeatures = collectFeatures(outputSlots);
        List<String> inputDisplayNames = collectSlotNames(slotsView.getSlotViews(RecipeIngredientRole.INPUT), ingredientManager);
        List<String> extraOutputDisplayNames = collectExtraOutputNames(outputSlots, ingredientManager);
        EncodePatternPacket encodePacket = createEncodePacket(recipeId, machineLabel, primaryOutput.displayName(), recipe,
                slotsView);
        boolean encodable = encodePacket != null;
        String identityKey = signatureOf(recipeId, machineKey, primaryOutput.displayName(), inputDisplayNames,
                extraOutputDisplayNames);

        return java.util.Optional.of(new RecipeFinderCandidateView(
                identityKey,
                primaryOutput.previewStack(),
                primaryOutput.displayName(),
                sourceModId,
                sourceModName,
                machineKey,
                machineLabel,
                recipeId == null ? "" : recipeId.toString(),
                outputItemIds,
                inputItemIds,
                involvedModIds,
                inputFeatures,
                outputFeatures,
                inputDisplayNames,
                extraOutputDisplayNames,
                encodable,
                encodePacket));
    }

    private static @Nullable EncodePatternPacket createEncodePacket(@Nullable ResourceLocation recipeId, String machineLabel,
            String displayName, Object recipe, IRecipeSlotsView slotsView) {
        List<List<GenericStack>> inputs = RecipeTransferPacketHelper.getGenericStacks(slotsView, RecipeIngredientRole.INPUT);
        List<GenericStack> outputs = RecipeTransferPacketHelper.getEncodingOutputs(recipe, slotsView);
        boolean hasInput = inputs.stream().anyMatch(Objects::nonNull);
        boolean hasOutput = outputs.stream().anyMatch(Objects::nonNull);
        boolean hasUnsupportedInput = inputs.stream().anyMatch(slot -> slot == null);
        boolean hasUnsupportedOutput = outputs.stream().anyMatch(Objects::isNull);

        if (!hasInput || !hasOutput || hasUnsupportedInput || hasUnsupportedOutput) {
            return null;
        }

        return new EncodePatternPacket(inputs, outputs, recipeId, displayName, "", machineLabel, false, false, false, false);
    }

    private static Set<String> collectFeatures(List<IRecipeSlotView> slots) {
        Set<String> features = new LinkedHashSet<>();
        for (IRecipeSlotView slot : slots) {
            for (ITypedIngredient<?> ingredient : slot.getAllIngredients().toList()) {
                features.addAll(RecipeFinderFeatureClassifier.classifyIngredient(ingredient.getIngredient()));
            }
        }
        if (features.isEmpty()) {
            features.add("other");
        }
        return Set.copyOf(features);
    }

    private static Set<String> collectItemIds(List<IRecipeSlotView> slots) {
        Set<String> ids = new LinkedHashSet<>();
        for (IRecipeSlotView slot : slots) {
            for (ItemStack stack : slot.getIngredients(VanillaTypes.ITEM_STACK).toList()) {
                if (!stack.isEmpty()) {
                    ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                }
            }
        }
        return Set.copyOf(ids);
    }

    private static Set<String> collectModIds(Set<String> outputItemIds, Set<String> inputItemIds) {
        Set<String> mods = new LinkedHashSet<>();
        for (String id : outputItemIds) {
            int i = id.indexOf(':');
            if (i > 0) mods.add(id.substring(0, i));
        }
        for (String id : inputItemIds) {
            int i = id.indexOf(':');
            if (i > 0) mods.add(id.substring(0, i));
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
        return List.copyOf(names);
    }

    private static List<String> collectExtraOutputNames(List<IRecipeSlotView> outputSlots, IIngredientManager ingredientManager) {
        List<String> names = new ArrayList<>();
        for (int i = 1; i < outputSlots.size(); i++) {
            DisplayedIngredient displayed = getDisplayed(outputSlots.get(i), ingredientManager);
            if (displayed != null) {
                names.add(displayed.displayName());
            }
        }
        return List.copyOf(names);
    }

    private static @Nullable DisplayedIngredient firstDisplayed(List<IRecipeSlotView> slots, IIngredientManager ingredientManager) {
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

        String displayName = getDisplayName(ingredientManager, ingredient);
        ItemStack previewStack = ingredient.getIngredient(VanillaTypes.ITEM_STACK)
                .map(ItemStack::copy)
                .orElseGet(() -> wrapIngredientPreview(ingredient.getIngredient()));
        return new DisplayedIngredient(displayName, previewStack);
    }

    private static String getDisplayName(IIngredientManager ingredientManager, ITypedIngredient<?> ingredient) {
        return getDisplayNameTyped(ingredientManager, ingredient);
    }

    private static <T> String getDisplayNameTyped(IIngredientManager ingredientManager, ITypedIngredient<?> ingredient) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> typed = (ITypedIngredient<T>) ingredient;
        return ingredientManager.getIngredientHelper(typed.getType()).getDisplayName(typed.getIngredient());
    }

    private static ItemStack wrapIngredientPreview(Object ingredient) {
        GenericStack generic = GenericIngredientUtil.toGenericStack(ingredient, 1);
        if (generic == null) {
            return ItemStack.EMPTY;
        }
        if (generic.what() instanceof AEItemKey itemKey) {
            return itemKey.toStack((int) Math.max(1, generic.amount()));
        }
        ItemStack wrapped = GenericStack.wrapInItemStack(generic);
        return wrapped == null ? ItemStack.EMPTY : wrapped;
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

    private static List<RecipeType<?>> getOrderedRecipeTypes(IJeiRuntime runtime) {
        List<RecipeType<?>> allTypes = new ArrayList<>(runtime.getJeiHelpers().getAllRecipeTypes().toList());
        allTypes.sort(Comparator.comparing(type -> type.getUid().toString()));
        return List.copyOf(allTypes);
    }

    public record BuildResult(List<RecipeFinderCandidateView> recipes, String statusMessage) {
    }

    private record DisplayedIngredient(String displayName, ItemStack previewStack) {
    }
}
