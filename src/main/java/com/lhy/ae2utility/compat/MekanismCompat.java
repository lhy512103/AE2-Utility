package com.lhy.ae2utility.compat;

import org.jetbrains.annotations.Nullable;

import mekanism.api.chemical.ChemicalStack;

public final class MekanismCompat {
    private MekanismCompat() {
    }

    public static boolean isChemicalStack(@Nullable Object ingredient) {
        return ingredient instanceof ChemicalStack<?>;
    }

    public static boolean isEmptyChemicalStack(@Nullable Object ingredient) {
        return ingredient instanceof ChemicalStack<?> stack && stack.isEmpty();
    }

    public static long getChemicalAmount(@Nullable Object ingredient) {
        if (ingredient instanceof ChemicalStack<?> stack && !stack.isEmpty()) {
            return Math.max(0L, stack.getAmount());
        }
        return 0L;
    }
}
