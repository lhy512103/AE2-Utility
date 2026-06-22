package com.lhy.ae2utility.util;

import org.jetbrains.annotations.Nullable;

import com.lhy.ae2utility.compat.AppliedMekanisticsCompat;
import com.lhy.ae2utility.compat.MekanismCompat;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fluids.FluidStack;

/**
 * 1.20.1 forge 版本：JEI 物品/流体/Mekanism 化学品 → AE2 {@link GenericStack} 转换。
 */
public final class GenericIngredientUtil {
    private static final String MEKANISM_MOD_ID = "mekanism";
    private static final String APPLIED_MEKANISTICS_MOD_ID = "appmek";

    private GenericIngredientUtil() {
    }

    public static @Nullable GenericStack toGenericStack(@Nullable ITypedIngredient<?> ingredient, long amount) {
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
            return AEItemKey.of(stack);
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
        long chemicalAmount = tryGetMekanismChemicalAmount(ingredient);
        if (chemicalAmount > 0) {
            return chemicalAmount;
        }
        return Math.max(1L, fallbackAmount);
    }

    public static boolean isMekanismChemicalStack(@Nullable Object ingredient) {
        if (!isMekanismLoaded()) {
            return false;
        }
        return MekanismCompat.isChemicalStack(ingredient);
    }

    public static boolean isEmptyMekanismChemicalStack(@Nullable Object ingredient) {
        if (!isMekanismLoaded()) {
            return false;
        }
        return MekanismCompat.isEmptyChemicalStack(ingredient);
    }

    public static long tryGetMekanismChemicalAmount(@Nullable Object ingredient) {
        if (!isMekanismLoaded()) {
            return 0L;
        }
        return MekanismCompat.getChemicalAmount(ingredient);
    }

    private static @Nullable AEKey toAppliedMekanisticsKey(@Nullable Object ingredient) {
        if (!isMekanismLoaded() || !isAppliedMekanisticsLoaded()) {
            return null;
        }
        return AppliedMekanisticsCompat.toAEKey(ingredient);
    }

    private static boolean isMekanismLoaded() {
        return ModList.get().isLoaded(MEKANISM_MOD_ID);
    }

    private static boolean isAppliedMekanisticsLoaded() {
        return ModList.get().isLoaded(APPLIED_MEKANISTICS_MOD_ID);
    }
}
