package com.lhy.ae2utility.util;

import org.jetbrains.annotations.Nullable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import mezz.jei.api.ingredients.ITypedIngredient;

/** JEI adapter; viewer-neutral callers must use RawIngredientUtil. */
public final class GenericIngredientUtil {
    private GenericIngredientUtil() {
    }

    public static @Nullable GenericStack toGenericStack(@Nullable ITypedIngredient<?> ingredient, long amount) {
        if (ingredient == null) {
            return null;
        }
        return toGenericStack(ingredient.getIngredient(), amount);
    }

    public static @Nullable GenericStack toGenericStack(@Nullable Object ingredient, long amount) {
        return RawIngredientUtil.toGenericStack(ingredient, amount);
    }

    public static @Nullable GenericStack toGenericStackExact(@Nullable Object ingredient, long amount) {
        return RawIngredientUtil.toGenericStackExact(ingredient, amount);
    }

    public static @Nullable AEKey toAEKey(@Nullable Object ingredient) {
        return RawIngredientUtil.toAEKey(ingredient);
    }

    public static long resolveAmount(@Nullable Object ingredient, long fallbackAmount) {
        return RawIngredientUtil.resolveAmount(ingredient, fallbackAmount);
    }

    public static boolean isMekanismChemicalStack(@Nullable Object ingredient) {
        return RawIngredientUtil.isMekanismChemicalStack(ingredient);
    }

    public static boolean isEmptyMekanismChemicalStack(@Nullable Object ingredient) {
        return RawIngredientUtil.isEmptyMekanismChemicalStack(ingredient);
    }

    public static long tryGetMekanismChemicalAmount(@Nullable Object ingredient) {
        return RawIngredientUtil.tryGetMekanismChemicalAmount(ingredient);
    }

}
