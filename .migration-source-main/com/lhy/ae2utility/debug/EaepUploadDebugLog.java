package com.lhy.ae2utility.debug;

import com.lhy.ae2utility.Ae2UtilityMod;

/**
 * EAEP 上传链路默认安静； JVM 传入 {@code -Dae2utility.debugEaepProviderSearch=true} 时写入 {@link #info}/{@link #warn}，
 * 用于排查搜索框回填、同步包次序等问题。
 */
public final class EaepUploadDebugLog {
    /** 设为 {@code true} 开启 EAEP 供应器弹出层回填相关日志。 */
    public static final boolean PROVIDER_SEARCH_DEBUG =
            Boolean.parseBoolean(System.getProperty("ae2utility.debugEaepProviderSearch"));

    private EaepUploadDebugLog() {
    }

    public static void info(String msg, Object... args) {
        if (!PROVIDER_SEARCH_DEBUG) {
            return;
        }
        if (args == null || args.length == 0) {
            Ae2UtilityMod.LOGGER.info("[ae2utility-eaep] {}", msg);
        } else {
            Ae2UtilityMod.LOGGER.info("[ae2utility-eaep] " + msg, args);
        }
    }

    public static void warn(String msg, Object... args) {
        if (!PROVIDER_SEARCH_DEBUG) {
            return;
        }
        if (args == null || args.length == 0) {
            Ae2UtilityMod.LOGGER.warn("[ae2utility-eaep] {}", msg);
        } else {
            Ae2UtilityMod.LOGGER.warn("[ae2utility-eaep] " + msg, args);
        }
    }

    public static void error(String msg, Throwable t) {
        Ae2UtilityMod.LOGGER.error(msg, t);
    }
}