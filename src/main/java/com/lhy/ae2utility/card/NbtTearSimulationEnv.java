package com.lhy.ae2utility.card;

import org.jetbrains.annotations.Nullable;

import appeng.me.service.CraftingService;

/**
 * 自动下单模拟期间提供 {@link CraftingService}，供处理配方按「供应该配方的样板供应器」校验撕裂卡。
 */
public final class NbtTearSimulationEnv {
    private static final ThreadLocal<CraftingService> SERVICE = new ThreadLocal<>();

    private NbtTearSimulationEnv() {
    }

    public static void beginCalculation(CraftingService cs) {
        SERVICE.set(cs);
    }

    /** CPU 执行提取配方时仅有服务引用。 */
    public static void beginCpuExtract(CraftingService cs) {
        SERVICE.set(cs);
    }

    public static void clear() {
        SERVICE.remove();
    }

    public static @Nullable CraftingService getCraftingService() {
        return SERVICE.get();
    }
}
