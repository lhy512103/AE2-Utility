package com.lhy.ae2utility.client;

import java.util.List;

import com.lhy.ae2utility.network.EncodePatternPacket;

/** 服务端下发的编码策略；未收到前只依赖服务端兜底。 */
public final class RemoteEncodeRules {
    private static volatile boolean received;
    private static volatile boolean blockFullCategoryBatch;
    private static volatile boolean requireOpenEncodingMenu;
    private static volatile int maxPatternsPerSession = -1;

    private RemoteEncodeRules() {
    }

    public static void receive(boolean blockFullCategory, boolean requireOpenMenu, int maxPatterns) {
        blockFullCategoryBatch = blockFullCategory;
        requireOpenEncodingMenu = requireOpenMenu;
        maxPatternsPerSession = maxPatterns;
        received = true;
    }

    public static void clear() {
        received = false;
    }

    public static boolean blocksFullCategoryBatch() {
        return received && blockFullCategoryBatch;
    }

    public static boolean requiresOpenEncodingMenu() {
        return received && requireOpenEncodingMenu;
    }

    public static List<EncodePatternPacket> capToSessionLimit(List<EncodePatternPacket> packets) {
        int max = received ? maxPatternsPerSession : -1;
        if (max <= 0 || packets.size() <= max) {
            return packets;
        }
        return List.copyOf(packets.subList(0, max));
    }
}
