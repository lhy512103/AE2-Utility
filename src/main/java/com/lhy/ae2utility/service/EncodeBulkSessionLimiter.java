package com.lhy.ae2utility.service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.Nullable;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.lhy.ae2utility.Ae2UtilityServerConfig;
import com.lhy.ae2utility.network.EncodePatternPacket;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** 服务端批量编码额度与聊天去重，额度包含等待供应器选择的样板。 */
public final class EncodeBulkSessionLimiter {
    private static final Cache<SessionKey, SessionState> SESSIONS = CacheBuilder.newBuilder()
            .maximumSize(4096)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();
    private static final long PLAIN_FULLCAT_CHAT_GAP_MS = 2500L;

    private EncodeBulkSessionLimiter() {
    }

    public static void clearFor(UUID playerId) {
        SESSIONS.asMap().keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    public static void clearAll() {
        SESSIONS.invalidateAll();
    }

    public static void cleanUp() {
        SESSIONS.cleanUp();
    }

    public static boolean rejectIfBlockedCategory(ServerPlayer player, EncodePatternPacket payload) {
        if (!Ae2UtilityServerConfig.blockJeiFullCategoryBatchEncode() || !payload.jeiFullCategoryBatch()) {
            return false;
        }
        notifyBlockedFullJeCategoryBatch(player, payload.bulkEncodeSessionId());
        sendSequentialUploadFailureEcho(player, payload);
        return true;
    }

    /** 在重复检查通过后、消耗空白样板前预留额度，失败退出时由 close 释放。 */
    public static @Nullable EncodingPermit reserve(ServerPlayer player, EncodePatternPacket payload) {
        int max = Ae2UtilityServerConfig.jeiBulkEncodeMaxPatternsPerSession();
        EncodingPermit permit = reserve(player.getUUID(), payload.bulkEncodeSessionId(), max);
        if (permit == null) {
            SessionState state = session(player.getUUID(), payload.bulkEncodeSessionId());
            boolean announce;
            synchronized (state) {
                announce = !state.overLimitAnnounced;
                state.overLimitAnnounced = true;
            }
            if (announce) {
                player.sendSystemMessage(Component.translatable("message.ae2utility.bulk_encode_exceeds_server_limit", max)
                        .withStyle(ChatFormatting.GOLD));
            }
            sendSequentialUploadFailureEcho(player, payload);
        }
        return permit;
    }

    static @Nullable EncodingPermit reserve(UUID playerId, int bulkId, int max) {
        if (bulkId == 0 || max <= 0) {
            return new EncodingPermit(null);
        }
        SessionState state = session(playerId, bulkId);
        synchronized (state) {
            if (state.used >= max) {
                return null;
            }
            state.used++;
        }
        return new EncodingPermit(state);
    }

    private static SessionState session(UUID playerId, int bulkId) {
        return SESSIONS.asMap().computeIfAbsent(new SessionKey(playerId, bulkId), key -> new SessionState());
    }

    private static void sendSequentialUploadFailureEcho(ServerPlayer player, EncodePatternPacket payload) {
        // 达到额度后结束顺序队列，避免继续发送余下请求。
        SequentialUploadResultBridge.sendImmediateResult(player, sequentialLabel(payload), false, payload.jeiSequentialQueue());
    }

    private static String sequentialLabel(EncodePatternPacket payload) {
        if (!payload.patternName().isBlank()) {
            return payload.patternName();
        }
        return payload.recipeId() != null ? payload.recipeId().toString() : "-";
    }

    private static boolean shouldAnnounceFullCategoryChat(UUID playerId, int bulkId) {
        SessionState state = session(playerId, bulkId);
        synchronized (state) {
            if (bulkId != 0) {
                boolean announce = !state.fullCategoryAnnounced;
                state.fullCategoryAnnounced = true;
                return announce;
            }
            long now = System.currentTimeMillis();
            long previous = state.lastFullCategoryChatMs;
            state.lastFullCategoryChatMs = now;
            return previous == 0 || now - previous >= PLAIN_FULLCAT_CHAT_GAP_MS;
        }
    }

    public static void notifyBlockedFullJeCategoryBatch(ServerPlayer player, int bulkSessionHint) {
        if (shouldAnnounceFullCategoryChat(player.getUUID(), bulkSessionHint)) {
            player.sendSystemMessage(Component.translatable("message.ae2utility.encode_rejected_full_category_blocked_server")
                    .withStyle(ChatFormatting.RED));
        }
    }

    private record SessionKey(UUID playerId, int bulkId) {}

    private static final class SessionState {
        private int used;
        private boolean fullCategoryAnnounced;
        private boolean overLimitAnnounced;
        private long lastFullCategoryChatMs;
    }

    public static final class EncodingPermit implements AutoCloseable {
        private final @Nullable SessionState state;
        private boolean committed;
        private boolean released;

        private EncodingPermit(@Nullable SessionState state) {
            this.state = state;
        }

        /** 样板已交付或进入供应器待上传状态，保留额度。 */
        public synchronized void commit() {
            committed = true;
        }

        public synchronized boolean isCommitted() {
            return committed && !released;
        }

        /** 退还空白样板时释放，包括此前已提交的待上传额度。 */
        public synchronized void release() {
            if (!released) {
                released = true;
                if (state != null) {
                    synchronized (state) {
                        state.used--;
                    }
                }
            }
        }

        @Override
        public synchronized void close() {
            if (!committed) {
                release();
            }
        }
    }
}
