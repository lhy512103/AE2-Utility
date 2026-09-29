package com.lhy.ae2utility.service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.lhy.ae2utility.Ae2UtilityServerConfig;
import com.lhy.ae2utility.network.EncodePatternPacket;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** 服务端按「玩家 + 批量会话 id」限制整类批量与单次批量条数，并对提示去重。 */
public final class EncodeBulkSessionLimiter {
    private static final ConcurrentMap<String, AtomicInteger> SESSION_COUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Boolean> ANNOUNCED = new ConcurrentHashMap<>();

    private EncodeBulkSessionLimiter() {
    }

    public static void clearFor(UUID playerId) {
        String prefix = playerId.toString() + '|';
        SESSION_COUNTS.keySet().removeIf(k -> k.startsWith(prefix));
        ANNOUNCED.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** @return {@code true} 表示本条请求应被拒绝 */
    public static boolean rejectIfLimited(ServerPlayer player, EncodePatternPacket payload) {
        if (!payload.isBatch()) {
            return false;
        }
        UUID uuid = player.getUUID();
        int sessionId = payload.bulkSessionId();
        if (ANNOUNCED.containsKey(uuid + "|" + sessionId + "|aborted")) {
            return true;
        }

        if (payload.fullCategoryBatch() && Ae2UtilityServerConfig.blockJeiFullCategoryBatchEncode()) {
            announceOnce(player, sessionId, "fullcat",
                    Component.translatable("message.ae2utility.encode_rejected_full_category_blocked_server")
                            .withStyle(ChatFormatting.RED));
            return true;
        }

        int max = Ae2UtilityServerConfig.jeiBulkEncodeMaxPatternsPerSession();
        if (max > 0) {
            int n = SESSION_COUNTS.computeIfAbsent(uuid + "|" + sessionId, k -> new AtomicInteger()).incrementAndGet();
            if (n > max) {
                announceOnce(player, sessionId, "over",
                        Component.translatable("message.ae2utility.bulk_encode_exceeds_server_limit", max)
                                .withStyle(ChatFormatting.GOLD));
                return true;
            }
        }
        return false;
    }

    public static void abortSession(ServerPlayer player, int sessionId) {
        ANNOUNCED.put(player.getUUID() + "|" + sessionId + "|aborted", Boolean.TRUE);
    }

    public static void announceOnce(ServerPlayer player, int sessionId, String topic, Component message) {
        if (ANNOUNCED.putIfAbsent(player.getUUID() + "|" + sessionId + "|" + topic, Boolean.TRUE) == null) {
            player.sendSystemMessage(message);
        }
    }
}
