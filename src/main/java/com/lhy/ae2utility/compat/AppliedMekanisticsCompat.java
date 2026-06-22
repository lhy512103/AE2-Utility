package com.lhy.ae2utility.compat;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEKey;
import mekanism.api.chemical.ChemicalStack;
import me.ramidzkh.mekae2.ae2.MekanismKey;

public final class AppliedMekanisticsCompat {
    private AppliedMekanisticsCompat() {
    }

    public static @Nullable AEKey toAEKey(@Nullable Object ingredient) {
        if (ingredient instanceof ChemicalStack<?> stack && !stack.isEmpty()) {
            return MekanismKey.of(stack);
        }
        return null;
    }
}
