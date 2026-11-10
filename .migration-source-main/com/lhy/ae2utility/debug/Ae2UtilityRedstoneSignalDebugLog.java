package com.lhy.ae2utility.debug;

import com.lhy.ae2utility.Ae2UtilityMod;

/**
 * 样板供应器发信卡红石调试。
 * <ul>
 *   <li>{@code -Dae2utility.debugRedstoneSignal=true}：下单/产物回传脉冲、计划刻等相关日志。</li>
 *   <li>{@code -Dae2utility.debugRedstoneWire=true}：<strong>极强刷屏</strong>，记录 {@code Block#getSignal}/{@code tick}
 *       ，仅排查「 Comparator 始终 0 」时再开片刻。</li>
 * </ul>
 * 两处默认均为 {@code false}；勿长期开启第二条。
 */
public final class Ae2UtilityRedstoneSignalDebugLog {
    public static final boolean PULSE_TRACE =
            Boolean.parseBoolean(System.getProperty("ae2utility.debugRedstoneSignal", "false"));

    public static final boolean WIRE_SPAM =
            Boolean.parseBoolean(System.getProperty("ae2utility.debugRedstoneWire", "false"));

    private static final String PREFIX = "[ae2utility-rs-debug] ";

    private Ae2UtilityRedstoneSignalDebugLog() {
    }

    public static void pulse(String msg, Object... args) {
        if (!PULSE_TRACE) {
            return;
        }
        if (args == null || args.length == 0) {
            Ae2UtilityMod.LOGGER.info("{}{}", PREFIX, msg);
        } else {
            Ae2UtilityMod.LOGGER.info(PREFIX + msg, args);
        }
    }

    public static void wire(String msg, Object... args) {
        if (!WIRE_SPAM) {
            return;
        }
        if (args == null || args.length == 0) {
            Ae2UtilityMod.LOGGER.info("{}wire {}", PREFIX, msg);
        } else {
            Ae2UtilityMod.LOGGER.info(PREFIX + "wire " + msg, args);
        }
    }
}
