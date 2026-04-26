package com.lhy.ae2utility.debug;

import java.util.concurrent.ConcurrentHashMap;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/**
 * NBT 撕裂卡解锁比较调试：默认在日志中输出（服务端 / 集成服逻辑线程）。
 * 关闭请设 JVM 参数 {@code -Dae2utility.debugNbtTear=false}；确认无问题后可改回 {@link #isEnabled()} 默认 false。
 *
 * <h2>复现步骤（便于在 latest.log 中看到 {@code onStackReturnedToNetwork} / 本类调试行）</h2>
 * <ol>
 * <li>样板供应器设置「锁定合成」为 <strong>LOCK_UNTIL_RESULT</strong>（等合成结果后再解锁）。</li>
 * <li>在撕裂卡槽放入 NBT 撕裂卡（可先测白名单为空，再测带白名单）。</li>
 * <li>推样板并开始一次合成，使产物从<strong>返还栏</strong>经注入进入 ME（不要只打开 GUI 就退出）。</li>
 * <li>保持世界运行直到至少出现一次「主输出键与返还键严格不等」的返还；不要进世界数秒就退出，否则日志里可能完全没有本模组相关行。</li>
 * </ol>
 */
public final class NbtTearCardDebug {
    private static final ConcurrentHashMap<String, Long> HEAD_MISMATCH_LAST_MS = new ConcurrentHashMap<>();
    private static final long HEAD_MISMATCH_COOLDOWN_MS = 5000L;

    private NbtTearCardDebug() {
    }

    public static boolean isEnabled() {
        String v = System.getProperty("ae2utility.debugNbtTear");
        if (v == null || v.isEmpty()) {
            return false;
        }
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    public static void logUnlockCompare(
            Object unlockWhat,
            Object returnedKey,
            boolean vanillaEquals,
            boolean finalResult,
            boolean hasCard,
            String filterSummary) {
    }

    /**
     * 已处于「等合成结果」且键不匹配，但服务端撕裂卡槽为空或非撕裂卡（常见于未同步到服务端或未换 jar）。
     */
    public static void logSkipNoTearCard(Object unlockWhat, Object returnedKey, boolean slotEmpty) {
    }

    /**
     * {@code PatternProviderLogic#onStackReturnedToNetwork} 入口：仅在 RESULT、已设 {@code unlockStack} 且
     * {@code unlockStack.what()} 与返还物键<strong>严格不等</strong>时打一条 INFO（短窗口指纹去重），用于区分「未进回调」与「进了但键不一致」。
     */
    public static void logStackReturnedStrictMismatchHead(
            GenericStack unlockStack,
            GenericStack returned,
            boolean tearSlotEmpty,
            boolean hasTearCardItem) {
        if (!isEnabled()) {
            return;
        }
        Object uw = unlockStack.what();
        Object rw = returned.what();
        String fp = summarizeKey(uw) + "||" + summarizeKey(rw);
        long now = System.currentTimeMillis();
        Long prev = HEAD_MISMATCH_LAST_MS.get(fp);
        if (prev != null && (now - prev) < HEAD_MISMATCH_COOLDOWN_MS) {
            return;
        }
        HEAD_MISMATCH_LAST_MS.put(fp, now);
    }

    public static void logGlobalFilter(String stage, Object filter) {
        if (!isEnabled()) {
            return;
        }
    }

    public static void logFuzzyCraftSearch(String stage, Object wanted, Object candidate, boolean accepted, String reason) {
        if (!isEnabled()) {
            return;
        }
    }

    public static void logPatternInputCheck(Object template, Object input, Object filter, boolean exact, boolean fuzzy, boolean result) {
        if (!isEnabled()) {
            return;
        }
    }

    public static void logMissingIngredient(Object what, long amount) {
        if (!isEnabled()) {
            return;
        }
    }

    private static String summarizeKey(Object k) {
        if (k == null) {
            return "null";
        }
        if (k instanceof AEKey ak) {
            return ak.getClass().getSimpleName() + "[" + ak + "]";
        }
        return k.getClass().getName() + "[" + k + "]";
    }
}
