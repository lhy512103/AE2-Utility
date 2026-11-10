package com.lhy.ae2utility.client.jei;

import java.util.Objects;

/** Per-layout preparation budget. Invalidating a context never grants a second budget in a tick. */
public final class EncodingPreparation {
    private long preparedTick = Long.MIN_VALUE;
    private Object context;

    public boolean contextChanged(Object nextContext) {
        if (Objects.equals(context, nextContext)) {
            return false;
        }
        context = nextContext;
        return true;
    }

    public boolean begin(long tick) {
        if (preparedTick == tick) {
            return false;
        }
        preparedTick = tick;
        return true;
    }

    public static boolean shouldPreview(boolean available, boolean hovered, boolean ingredients, boolean terminal) {
        return available && hovered && (ingredients || terminal);
    }
}
