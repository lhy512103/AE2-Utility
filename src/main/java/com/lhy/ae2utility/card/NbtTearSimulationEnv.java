package com.lhy.ae2utility.card;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.me.service.CraftingService;

/**
 * 自动下单模拟期间提供 {@link CraftingService}（及可选的模拟请求方），供处理配方按「供应该配方的样板供应器」校验撕裂卡。
 */
public final class NbtTearSimulationEnv {
    private static final ThreadLocal<CraftingService> SERVICE = new ThreadLocal<>();
    private static final ThreadLocal<ICraftingSimulationRequester> REQUESTER = new ThreadLocal<>();
    private static final ThreadLocal<Set<String>> WARNED_KEYS = new ThreadLocal<>();

    private NbtTearSimulationEnv() {
    }

    public static void beginCalculation(CraftingService cs, ICraftingSimulationRequester requester) {
        SERVICE.set(cs);
        REQUESTER.set(requester);
        WARNED_KEYS.set(new HashSet<>());
    }

    /** CPU 执行提取配方时仅有服务引用，不发送聊天提示、不做去重集合。 */
    public static void beginCpuExtract(CraftingService cs) {
        SERVICE.set(cs);
        REQUESTER.set(null);
        WARNED_KEYS.set(null);
    }

    public static void clear() {
        SERVICE.remove();
        REQUESTER.remove();
        WARNED_KEYS.remove();
    }

    public static @Nullable CraftingService getCraftingService() {
        return SERVICE.get();
    }

    public static @Nullable ICraftingSimulationRequester getRequester() {
        return REQUESTER.get();
    }

    /**
     * @return {@code true} 若应展示提示（计算路径下且该去重键首次出现）
     */
    public static boolean shouldEmitProcessingTearHint(String dedupeKey) {
        Set<String> s = WARNED_KEYS.get();
        if (s == null) {
            return false;
        }
        return s.add(dedupeKey);
    }
}
