package com.lhy.ae2utility.debug;

import com.lhy.ae2utility.Ae2UtilityMod;

/**
 * Recipe Tree Overview 临时性能调试。
 * 默认关闭；需要时在 JVM 参数中加 {@code -Dae2utility.debugRecipeTreePerf=true}。
 */
public final class RecipeTreePerfDebug {
    private static final long RENDER_SUMMARY_COOLDOWN_MS = 1000L;
    private static volatile long lastRenderSummaryMs;

    private RecipeTreePerfDebug() {
    }

    public static boolean isEnabled() {
        String v = System.getProperty("ae2utility.debugRecipeTreePerf");
        if (v == null || v.isEmpty()) {
            return false;
        }
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    public static long begin() {
        return isEnabled() ? System.nanoTime() : 0L;
    }

    public static void logPhase(String phase, long startedAtNanos, String details, Object... args) {
        if (!isEnabled()) {
            return;
        }
        long elapsedMicros = (System.nanoTime() - startedAtNanos) / 1000L;
        Object[] merged = new Object[args.length + 2];
        merged[0] = phase;
        merged[1] = elapsedMicros;
        System.arraycopy(args, 0, merged, 2, args.length);
        Ae2UtilityMod.LOGGER.info("[RECIPE_TREE_PERF][{}] {} us | " + details, merged);
    }

    public static void logRenderSummary(long startedAtNanos, String details, Object... args) {
        if (!isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRenderSummaryMs < RENDER_SUMMARY_COOLDOWN_MS) {
            return;
        }
        lastRenderSummaryMs = now;
        logPhase("render_summary", startedAtNanos, details, args);
    }
}
