package com.lhy.ae2utility.card;

import org.jetbrains.annotations.Nullable;

/**
 * During a crafting-plan simulation or CPU pattern-extraction, holds a pre-computed
 * {@link NbtTearFilter} merged from ALL pattern providers in the network that carry a tear card.
 * Scoped per operation; nested simulations restore the enclosing filter when done.
 */
public final class NbtTearCraftingContext {
    private static final ThreadLocal<NbtTearFilter> FILTER = new ThreadLocal<>();

    public static NbtTearScope enter(@Nullable NbtTearFilter filter) {
        return NbtTearScope.enter(FILTER, filter);
    }

    public static void set(@Nullable NbtTearFilter filter) {
        if (filter != null) {
            FILTER.set(filter);
        } else {
            FILTER.remove();
        }
    }

    @Nullable
    public static NbtTearFilter get() {
        return FILTER.get();
    }

    public static void clear() {
        FILTER.remove();
    }

    private NbtTearCraftingContext() {}
}
