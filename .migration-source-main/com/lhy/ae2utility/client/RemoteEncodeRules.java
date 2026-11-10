package com.lhy.ae2utility.client;

import java.util.List;

import com.lhy.ae2utility.network.EncodePatternPacket;

/**
 * 由 {@link com.lhy.ae2utility.network.SyncAe2UtilityEncodeRulesPacket} 写入，供 JEI / 批量入口在发包前预判。
 */
public final class RemoteEncodeRules {
    /** 接入服务器前不向玩家提示「服务端禁止」——避免误判单机未同步瞬间。*/
    private static volatile boolean received;
    private static volatile boolean blockJeFullCategoryBatchEncode;
    private static volatile boolean requireOpenPatternEncodingMenuForJe;
    private static volatile int jeiBulkEncodeMaxPatternsPerSession = Integer.MAX_VALUE;

    private RemoteEncodeRules() {
    }

    /** 断开连接或未收到包时仍为 false（此时仅依赖服务端兜底）。*/
    public static boolean hasSyncedRulesFromServer() {
        return received;
    }

    public static boolean remoteBlocksJeFullCategoryBatch() {
        return received && blockJeFullCategoryBatchEncode;
    }

    public static boolean remoteRequiresOpenEncodingMenuForJe() {
        return received && requireOpenPatternEncodingMenuForJe;
    }

    public static int remoteMaxJeBulkEncodePatternsPerSession() {
        return received ? jeiBulkEncodeMaxPatternsPerSession : Integer.MAX_VALUE;
    }

    /**
     * 已同步规则且超过 {@link #remoteMaxJeBulkEncodePatternsPerSession()} 时，仅保留列表前若干条用于发送；否则返回原列表引用。
     */
    public static List<EncodePatternPacket> capPacketsToServerBulkLimit(List<EncodePatternPacket> packets) {
        if (!hasSyncedRulesFromServer() || packets.isEmpty()) {
            return packets;
        }
        int max = remoteMaxJeBulkEncodePatternsPerSession();
        if (max <= 0 || packets.size() <= max) {
            return packets;
        }
        return List.copyOf(packets.subList(0, max));
    }

    /** 服务端同步（主线程 enqueue 调用）。{@code maxBulkPatterns == -1} 表示关闭上限，在客户端侧等价于无截断。*/
    public static void receiveFromServer(boolean blockJeFullCat, boolean requireOpenMenu, int maxBulkPatterns) {
        received = true;
        blockJeFullCategoryBatchEncode = blockJeFullCat;
        requireOpenPatternEncodingMenuForJe = requireOpenMenu;
        jeiBulkEncodeMaxPatternsPerSession = maxBulkPatterns == -1 ? Integer.MAX_VALUE : maxBulkPatterns;
    }

    /** 返回本地世界时仍可保留上一轮值；服务端会在再次登录时覆盖。*/
    public static void clearOnDisconnected() {
        received = false;
    }
}
