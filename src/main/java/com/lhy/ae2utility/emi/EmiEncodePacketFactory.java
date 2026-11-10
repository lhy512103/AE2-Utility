package com.lhy.ae2utility.emi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.lhy.ae2utility.client.Ae2UtilityClientConfig;
import com.lhy.ae2utility.integration.multiblock.MultiblockStructurePatternCompat;
import com.lhy.ae2utility.client.EncodePatternDrafts;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.util.RawIngredientUtil;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluid;

public final class EmiEncodePacketFactory {
    private EmiEncodePacketFactory() {
    }

    public static boolean isMultiblockStructureRecipe(@Nullable EmiRecipe recipe) {
        if (recipe == null) {
            return false;
        }
        Object backingRecipe = recipe.getBackingRecipe();
        Object structureRecipe = resolveStructureRecipe(recipe, backingRecipe);
        return MultiblockStructurePatternCompat.isSupportedRecipe(
                structureRecipe, hasStructureInputs(recipe.getInputs()), hasActualOutputs(recipe.getOutputs()));
    }

    public static Optional<EncodePatternPacket> tryCreate(EmiRecipe recipe, boolean upload) {
        Object backingRecipe = recipe.getBackingRecipe();
        Object structureRecipe = resolveStructureRecipe(recipe, backingRecipe);
        List<GenericStack> outputs = toOutputs(recipe.getOutputs());
        boolean multiblockStructureDraft = MultiblockStructurePatternCompat.isSupportedRecipe(
                structureRecipe, hasStructureInputs(recipe.getInputs()), hasActualOutputs(recipe.getOutputs()));
        if (multiblockStructureDraft) {
            GenericStack draftOutput = MultiblockStructurePatternCompat.createDraftOutput(structureRecipe);
            if (draftOutput == null) {
                return Optional.empty();
            }
            outputs = List.of(draftOutput);
        }
        List<EmiIngredient> encodingInputs = multiblockStructureDraft
                ? recipe.getInputs().stream()
                        .filter(ingredient -> !isFilteredMultiblockInput(ingredient))
                        .toList()
                : recipe.getInputs();
        List<List<GenericStack>> inputs = toInputSlots(encodingInputs, EncodePatternDrafts.outputKeys(outputs));
        if (multiblockStructureDraft && !EncodePatternDrafts.hasMeaningfulInputs(inputs)) {
            inputs = MultiblockStructurePatternCompat.extractStructureInputs(structureRecipe).stream()
                    .map(List::of)
                    .toList();
        }

        ResourceLocation recipeId = multiblockStructureDraft ? null : recipe.getId();
        String providerSearchKey = EncodePatternDrafts.eaepUploadAvailable(upload)
                ? eaepSearchKey(recipe, backingRecipe) : "";
        boolean craftingCategoryHint = !multiblockStructureDraft && isCraftingCategory(recipe, backingRecipe, inputs);
        Object identityRecipe = backingRecipe != null ? backingRecipe : structureRecipe;
        return EncodePatternDrafts.packetIfEncodable(new EncodePatternDrafts.Draft(inputs, outputs, recipeId,
                providerSearchKey, upload, craftingCategoryHint, false, 0, identityRecipe));
    }

    private static Object resolveStructureRecipe(EmiRecipe recipe, @Nullable Object backingRecipe) {
        if (recipe instanceof dev.emi.emi.jemi.JemiRecipe<?> jemi && jemi.recipe != null) {
            return jemi.recipe;
        }
        return backingRecipe != null ? backingRecipe : recipe;
    }

    private static boolean hasStructureInputs(List<EmiIngredient> ingredients) {
        return ingredients.stream().anyMatch(ingredient -> ingredient != null && !ingredient.isEmpty()
                && ingredient.getEmiStacks().stream().anyMatch(stack -> stack != null
                        && !stack.isEmpty() && !stack.getItemStack().isEmpty()));
    }

    private static boolean hasActualOutputs(List<EmiStack> outputs) {
        return outputs.stream().anyMatch(stack -> stack != null && !stack.isEmpty());
    }

    private static boolean isFilteredMultiblockInput(@Nullable EmiIngredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return false;
        }
        List<ItemStack> alternatives = ingredient.getEmiStacks().stream()
                .map(EmiStack::getItemStack)
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
        return MultiblockStructurePatternCompat.isFilteredHatchSlot(alternatives);
    }

    private static List<List<GenericStack>> toInputSlots(List<EmiIngredient> ingredients,
            java.util.Set<AEKey> outputKeys) {
        List<List<GenericStack>> slots = new ArrayList<>();
        List<AEKey> favoriteKeys = favoriteKeys().stream()
                .filter(key -> !outputKeys.contains(key))
                .toList();
        for (EmiIngredient ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()) {
                slots.add(List.of());
                continue;
            }
            List<GenericStack> alternatives = new ArrayList<>();
            for (EmiStack stack : ingredient.getEmiStacks()) {
                GenericStack generic = toGenericStack(stack, ingredient.getAmount());
                if (generic == null) {
                    continue;
                }
                boolean seen = false;
                for (GenericStack existing : alternatives) {
                    if (existing.what().equals(generic.what())) {
                        seen = true;
                        break;
                    }
                }
                if (!seen) {
                    alternatives.add(generic);
                }
            }
            if (alternatives.isEmpty()) {
                slots.add(null);
                continue;
            }
            GenericStack favorite = firstFavoriteAlternative(alternatives, favoriteKeys);
            slots.add(favorite == null ? alternatives : List.of(favorite));
        }
        return slots;
    }

    private static List<AEKey> favoriteKeys() {
        if (!Ae2UtilityClientConfig.preferFavoritesForPatternEncoding()) {
            return List.of();
        }

        List<AEKey> keys = new ArrayList<>();
        for (EmiStack stack : EmiFavoriteStacks.snapshot()) {
            GenericStack generic = toGenericStack(stack, stack.getAmount());
            if (generic != null && generic.what() != null && !keys.contains(generic.what())) {
                keys.add(generic.what());
            }
        }
        return keys;
    }

    private static @Nullable GenericStack firstFavoriteAlternative(List<GenericStack> alternatives,
            List<AEKey> favoriteKeys) {
        for (AEKey favoriteKey : favoriteKeys) {
            for (GenericStack alternative : alternatives) {
                if (favoriteKey.equals(alternative.what())) {
                    return alternative;
                }
            }
        }
        return null;
    }

    private static List<GenericStack> toOutputs(List<EmiStack> stacks) {
        List<GenericStack> outputs = new ArrayList<>();
        for (EmiStack stack : stacks) {
            if (stack == null || stack.isEmpty() || stack.getChance() < 1.0f) {
                continue;
            }
            GenericStack generic = toGenericStack(stack, stack.getAmount());
            if (generic != null) {
                outputs.add(generic);
            } else {
                outputs.add(null);
            }
        }
        return outputs;
    }

    /**
     * Whether any candidate of the given EMI ingredient currently has a pattern / is
     * auto-craftable, mirroring the JEI-side per-slot highlight logic.
     */
    public static boolean isIngredientCraftable(@Nullable EmiIngredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return false;
        }
        for (EmiStack stack : ingredient.getEmiStacks()) {
            GenericStack generic = toGenericStack(stack, ingredient.getAmount());
            if (generic != null && generic.what() != null
                    && com.lhy.ae2utility.jei.CraftableStateCache.isCraftable(generic.what())) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable GenericStack toGenericStack(@Nullable EmiStack stack, long fallbackAmount) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        long amount = Math.max(1L, stack.getAmount() > 0 ? stack.getAmount() : fallbackAmount);
        Object key = stack.getKey();
        if (key instanceof Item item) {
            ItemStack itemStack = stack.getItemStack();
            if (itemStack.isEmpty()) {
                itemStack = item.getDefaultInstance();
            }
            itemStack.setCount((int) Math.min(Integer.MAX_VALUE, amount));
            return new GenericStack(AEItemKey.of(itemStack), amount);
        }
        if (key instanceof Fluid fluid) {
            return new GenericStack(AEFluidKey.of(fluid), amount);
        }
        return tryMekanismChemical(stack, amount);
    }

    /**
     * Converts a Mekanism chemical EMI stack (e.g. {@code ChemicalEmiStack}) into a
     * GenericStack via Applied Mekanistics. Mekanism's EMI stacks expose a public
     * {@code getStack()} returning a {@code mekanism.api.chemical.ChemicalStack}, which
     * {@link RawIngredientUtil#toGenericStack(Object, long)} already knows how to
     * map to a {@code MekanismKey}. Reflective so Mekanism stays an optional dependency.
     */
    private static @Nullable GenericStack tryMekanismChemical(EmiStack stack, long fallbackAmount) {
        try {
            var method = stack.getClass().getMethod("getStack");
            Object chemicalStack = method.invoke(stack);
            if (chemicalStack == null
                    || !"mekanism.api.chemical.ChemicalStack".equals(chemicalStack.getClass().getName())) {
                return null;
            }
            return RawIngredientUtil.toGenericStack(chemicalStack, fallbackAmount);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static String eaepSearchKey(EmiRecipe recipe, @Nullable Object backingRecipe) {
        // JEI 桥接的 EMI 配方把原始配方放在 JemiRecipe.recipe；原生 EMI 配方通过 getBackingRecipe() 暴露。
        String key = EncodePatternDrafts.eaepSearchKey(resolveMcRecipe(recipe, backingRecipe));
        if (key.isEmpty() && backingRecipe != null) {
            key = EncodePatternDrafts.eaepSearchKey(backingRecipe);
        }
        // 原生 EMI 配方（AAE 反应仓等）常常没有 RecipeManager 条目，此时用配方分类名，它通常就是供应器对应的机器名。
        return key.isEmpty() ? categoryName(recipe) : key;
    }

    private static @Nullable Recipe<?> resolveMcRecipe(EmiRecipe recipe,
            @Nullable Object backingRecipe) {
        Object raw = backingRecipe;
        if (recipe instanceof dev.emi.emi.jemi.JemiRecipe<?> jemi && jemi.recipe != null) {
            raw = jemi.recipe;
        }
        return raw instanceof Recipe<?> holder ? holder : null;
    }

    private static String categoryName(EmiRecipe recipe) {
        try {
            var category = recipe.getCategory();
            if (category != null && category.getName() != null) {
                String name = category.getName().getString();
                return name == null ? "" : name.trim();
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static boolean isCraftingCategory(EmiRecipe recipe, @Nullable Object backingRecipe,
            List<List<GenericStack>> inputs) {
        if (!EncodePatternDrafts.fitsCraftingGrid(inputs)) {
            return false;
        }
        if (backingRecipe instanceof CraftingRecipe) {
            return true;
        }
        return recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING;
    }
}
