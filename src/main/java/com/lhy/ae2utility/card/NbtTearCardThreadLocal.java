package com.lhy.ae2utility.card;

public class NbtTearCardThreadLocal {
    private static final ThreadLocal<NbtTearFilter> FILTER = new ThreadLocal<>();

    public static void set(NbtTearFilter filter) {
        FILTER.set(filter);
    }

    public static NbtTearFilter get() {
        return FILTER.get();
    }

    public static void clear() {
        FILTER.remove();
    }
}
