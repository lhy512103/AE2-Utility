package com.lhy.ae2utility.card;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;

/** Current pattern whose inputs are being validated (simulation or CPU extraction). */
public final class NbtTearPatternContext {
    private static final ThreadLocal<IPatternDetails> PATTERN = new ThreadLocal<>();

    public static void set(@Nullable IPatternDetails details) {
        PATTERN.set(details);
    }

    @Nullable
    public static IPatternDetails get() {
        return PATTERN.get();
    }

    public static void clear() {
        PATTERN.remove();
    }

    private NbtTearPatternContext() {}
}
