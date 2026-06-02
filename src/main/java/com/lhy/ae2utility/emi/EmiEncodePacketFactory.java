package com.lhy.ae2utility.emi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import com.lhy.ae2utility.integration.eaep.EaepReflection;
import com.lhy.ae2utility.jei.JeiPatternSubstitutionUi;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.util.GenericIngredientUtil;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;

public final class EmiEncodePacketFactory {
    private EmiEncodePacketFactory() {
    }

    public static Optional<EncodePatternPacket> tryCreate(EmiRecipe recipe, boolean upload) {
        List<List<GenericStack>> inputs = toInputSlots(recipe.getInputs());
        List<GenericStack> outputs = toOutputs(recipe.getOutputs());

        boolean hasInput = inputs.stream().anyMatch(slot -> slot != null && !slot.isEmpty());
        boolean hasOutput = outputs.stream().anyMatch(Objects::nonNull);
        boolean hasUnsupportedInput = inputs.stream().anyMatch(Objects::isNull);
        boolean hasUnsupportedOutput = outputs.stream().anyMatch(Objects::isNull);
        if (!hasInput || !hasOutput || hasUnsupportedInput || hasUnsupportedOutput) {
            return Optional.empty();
        }

        ResourceLocation recipeId = toResourceLocation(recipe.getId());
        Object backingRecipe = recipe.getBackingRecipe();
        String patternName = derivePatternName(recipeId, outputs);
        String providerSearchKey = computeEaepProviderSearchKey(recipe, backingRecipe, upload);
        boolean craftingCategoryHint = isCraftingCategory(recipe, backingRecipe, inputs);

        return Optional.of(new EncodePatternPacket(
                inputs,
                outputs,
                recipeId,
                patternName,
                providerSearchKey,
                providerSearchKey,
                upload,
                JeiPatternSubstitutionUi.isItemSubstituteOn(),
                JeiPatternSubstitutionUi.isFluidSubstituteOn(),
                false,
                false,
                false,
                0,
                craftingCategoryHint));
    }

    private static List<List<GenericStack>> toInputSlots(List<EmiIngredient> ingredients) {
        List<List<GenericStack>> slots = new ArrayList<>();
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
            slots.add(alternatives.isEmpty() ? null : alternatives);
        }
        return slots;
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
     * {@link GenericIngredientUtil#toGenericStack(Object, long)} already knows how to
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
            return GenericIngredientUtil.toGenericStack(chemicalStack, fallbackAmount);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static @Nullable ResourceLocation toResourceLocation(@Nullable net.minecraft.resources.ResourceLocation id) {
        return id;
    }

    private static String derivePatternName(@Nullable ResourceLocation recipeId, List<GenericStack> outputs) {
        for (GenericStack output : outputs) {
            if (output == null || output.what() == null) {
                continue;
            }
            var name = output.what().getDisplayName();
            if (name != null && !name.getString().isBlank()) {
                return name.getString();
            }
        }
        return recipeId != null ? recipeId.toString() : "-";
    }

    private static String computeEaepProviderSearchKey(EmiRecipe recipe, @Nullable Object backingRecipe,
            boolean upload) {
        if (!upload || !ModList.get().isLoaded("extendedae_plus")) {
            return "";
        }

        // Resolve the actual Minecraft recipe object. JEI-bridged EMI recipes carry the
        // raw JEI recipe in JemiRecipe.recipe; native EMI recipes expose it via getBackingRecipe().
        net.minecraft.world.item.crafting.Recipe<?> mcRecipe = resolveMcRecipe(recipe, backingRecipe);
        if (mcRecipe instanceof CraftingRecipe) {
            String key = EaepReflection.defaultCraftingSearchKey();
            if (key != null && !key.isEmpty()) {
                return key;
            }
        } else if (mcRecipe != null) {
            String key = EaepReflection.mapRecipeTypeToSearchKey(mcRecipe);
            if (key != null && !key.isEmpty()) {
                return key;
            }
        }

        if (backingRecipe != null) {
            String key = EaepReflection.deriveSearchKeyFromUnknownRecipe(backingRecipe);
            if (key != null && !key.isEmpty()) {
                return key;
            }
        }

        // Fallback: native EMI recipes (AAE reaction chamber, etc.) often have no
        // RecipeManager entry, so use the recipe category's display name as the provider
        // search key — it matches the machine name the providers are named after.
        return categoryName(recipe);
    }

    private static net.minecraft.world.item.crafting.@Nullable Recipe<?> resolveMcRecipe(EmiRecipe recipe,
            @Nullable Object backingRecipe) {
        Object raw = backingRecipe;
        if (recipe instanceof dev.emi.emi.jemi.JemiRecipe<?> jemi && jemi.recipe != null) {
            raw = jemi.recipe;
        }
        if (raw instanceof RecipeHolder<?> holder) {
            return holder.value();
        }
        if (raw instanceof net.minecraft.world.item.crafting.Recipe<?> r) {
            return r;
        }
        return null;
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
        long meaningfulInputs = inputs.stream().filter(slot -> slot != null && !slot.isEmpty()).count();
        if (meaningfulInputs > 9) {
            return false;
        }
        if (backingRecipe instanceof RecipeHolder<?> holder && holder.value() instanceof CraftingRecipe) {
            return true;
        }
        return recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING;
    }
}
