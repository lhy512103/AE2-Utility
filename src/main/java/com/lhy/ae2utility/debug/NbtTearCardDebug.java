package com.lhy.ae2utility.debug;

import java.util.concurrent.ConcurrentHashMap;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.world.item.ItemStack;

import com.lhy.ae2utility.Ae2UtilityMod;

/**
 * NBT 撕裂卡解锁比较调试（服务端 / 集成服逻辑线程）。
 * 默认关闭；需要时在 JVM 参数中加 {@code -Dae2utility.debugNbtTear=true}。
 *
 * <h2>复现步骤（需先启用上述 JVM 参数，便于在 latest.log 中看到调试行）</h2>
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
        if (!isEnabled()) {
            return;
        }
        Ae2UtilityMod.LOGGER.info("[NBT_TEAR][unlock_compare] unlock={} returned={} vanillaEquals={} finalResult={} hasCard={} filter={}",
                summarizeKey(unlockWhat), summarizeKey(returnedKey), vanillaEquals, finalResult, hasCard, filterSummary);
    }

    /**
     * 已处于「等合成结果」且键不匹配，但服务端撕裂卡槽为空或非撕裂卡（常见于未同步到服务端或未换 jar）。
     */
    public static void logSkipNoTearCard(Object unlockWhat, Object returnedKey, boolean slotEmpty) {
        if (!isEnabled()) {
            return;
        }
        Ae2UtilityMod.LOGGER.info("[NBT_TEAR][unlock_skip_no_card] unlock={} returned={} slotEmpty={}",
                summarizeKey(unlockWhat), summarizeKey(returnedKey), slotEmpty);
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
        Ae2UtilityMod.LOGGER.info(
                "[NBT_TEAR][returned_head_mismatch] unlock={} returned={} tearSlotEmpty={} hasTearCardItem={}",
                summarizeKey(uw), summarizeKey(rw), tearSlotEmpty, hasTearCardItem);
    }

    public static void logGlobalFilter(String stage, Object filter) {
        if (!isEnabled()) {
            return;
        }
        Ae2UtilityMod.LOGGER.info("[NBT_TEAR][global_filter] stage={} filter={}", stage, summarize(filter));
    }

    public static void logFuzzyCraftSearch(String stage, Object wanted, Object candidate, boolean accepted, String reason) {
        if (!isEnabled()) {
            return;
        }
        Ae2UtilityMod.LOGGER.info("[NBT_TEAR][fuzzy] stage={} wanted={} candidate={} accepted={} reason={}",
                stage, summarizeKey(wanted), summarize(candidate), accepted, reason);
    }

    public static void logPatternInputCheck(Object template, Object input, Object filter, boolean exact, boolean fuzzy, boolean result) {
        if (!isEnabled()) {
            return;
        }
        Ae2UtilityMod.LOGGER.info("[NBT_TEAR][pattern_input] template={} input={} filter={} exact={} fuzzy={} result={}",
                summarizeKey(template), summarizeKey(input), summarize(filter), exact, fuzzy, result);
    }

    public static void logMissingIngredient(Object what, long amount) {
        if (!isEnabled()) {
            return;
        }
        Ae2UtilityMod.LOGGER.info("[NBT_TEAR][missing] what={} amount={}", summarizeKey(what), amount);
    }

    public static void logPatternContext(String stage, Object pattern, Object craftingService, Object template, Object input,
            String reason) {
        if (!isEnabled()) {
            return;
        }
        Ae2UtilityMod.LOGGER.info(
                "[NBT_TEAR][pattern_context] stage={} pattern={} craftingService={} template={} input={} reason={}",
                stage, summarize(pattern), summarize(craftingService), summarizeKey(template), summarizeKey(input), reason);
    }

    public static void logProviderCheck(String stage, Object pattern, Object provider, Object card, Object filter,
            boolean accepted, String reason) {
        if (!isEnabled()) {
            return;
        }
        Ae2UtilityMod.LOGGER.info(
                "[NBT_TEAR][provider_check] stage={} pattern={} provider={} card={} filter={} accepted={} reason={}",
                stage, summarize(pattern), summarize(provider), summarize(card), summarize(filter), accepted, reason);
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

    private static String summarize(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof AEKey) {
            return summarizeKey(value);
        }
        if (value instanceof ItemStack stack) {
            return "ItemStack[" + stack + "]";
        }
        return value.getClass().getName() + "[" + value + "]";
    }
}
