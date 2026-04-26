package com.lhy.ae2utility.util;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public final class GenericIngredientUtil {
    private GenericIngredientUtil() {
    }

    public static @Nullable GenericStack toGenericStack(@Nullable ITypedIngredient<?> ingredient, int amount) {
        if (ingredient == null) {
            return null;
        }
        return toGenericStack(ingredient.getIngredient(), amount);
    }

    public static @Nullable GenericStack toGenericStack(@Nullable Object ingredient, long amount) {
        AEKey key = toAEKey(ingredient);
        if (key == null) {
            return null;
        }
        long normalizedAmount = Math.max(1L, resolveAmount(ingredient, amount));
        return new GenericStack(key, normalizedAmount);
    }

    public static @Nullable AEKey toAEKey(@Nullable Object ingredient) {
        if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
            return appeng.api.stacks.AEItemKey.of(stack);
        }
        if (ingredient instanceof FluidStack fluid && !fluid.isEmpty()) {
            return AEFluidKey.of(fluid);
        }
        return toAppliedMekanisticsKey(ingredient);
    }

    public static long resolveAmount(@Nullable Object ingredient, long fallbackAmount) {
        if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
            return Math.max(1, stack.getCount() > 0 ? stack.getCount() : fallbackAmount);
        }
        if (ingredient instanceof FluidStack fluid && !fluid.isEmpty()) {
            return Math.max(1, fluid.getAmount() > 0 ? fluid.getAmount() : fallbackAmount);
        }
        long mekanismAmount = tryGetMekanismChemicalAmount(ingredient);
        if (mekanismAmount > 0) {
            return mekanismAmount;
        }
        return Math.max(1L, fallbackAmount);
    }

    public static long tryGetMekanismChemicalAmount(@Nullable Object ingredient) {
        if (ingredient == null || !"mekanism.api.chemical.ChemicalStack".equals(ingredient.getClass().getName())) {
            return 0L;
        }
        try {
            Object rawAmount = ingredient.getClass().getMethod("getAmount").invoke(ingredient);
            if (rawAmount instanceof Number number) {
                return Math.max(0L, number.longValue());
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return 0L;
    }

    private static @Nullable AEKey toAppliedMekanisticsKey(@Nullable Object ingredient) {
        if (ingredient == null || !"mekanism.api.chemical.ChemicalStack".equals(ingredient.getClass().getName())) {
            return null;
        }
        try {
            Class<?> chemicalStackClass = ingredient.getClass();
            Class<?> mekanismKeyClass = Class.forName("me.ramidzkh.mekae2.ae2.MekanismKey");
            Object key = mekanismKeyClass.getMethod("of", chemicalStackClass).invoke(null, ingredient);
            return key instanceof AEKey aeKey ? aeKey : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
