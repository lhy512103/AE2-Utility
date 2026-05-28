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

/** 服务端：整类分页拒绝聊天去重；同一会话 bulk id 下一次操作内最大样板编码数（单条 EncodePatternPacket）。 */
public final class EncodeBulkSessionLimiter {

    private static final ConcurrentMap<String, AtomicInteger> SESSION_COUNTS = new ConcurrentHashMap<>();
    /** key uuid|bulkId|fullcat_chat */
    private static final ConcurrentMap<String, Boolean> FULL_CAT_CHAT_DONE = new ConcurrentHashMap<>();
    /** key uuid|bulkId|over_chat */
    private static final ConcurrentMap<String, Boolean> OVER_LIMIT_CHAT_DONE = new ConcurrentHashMap<>();

    /** bulkSessionId == 0 且仍伪造整类标记时按 UUID 防抖 */
    private static final ConcurrentMap<UUID, Long> PLAIN_FULLCAT_LAST_CHAT_MS = new ConcurrentHashMap<>();
    private static final long PLAIN_FULLCAT_CHAT_GAP_MS = 2500L;

    private EncodeBulkSessionLimiter() {
    }

    public static void clearFor(UUID playerId) {
        String needle = playerId.toString() + '|';
        SESSION_COUNTS.keySet().removeIf(k -> k.startsWith(needle));
        FULL_CAT_CHAT_DONE.keySet().removeIf(k -> k.startsWith(needle));
        OVER_LIMIT_CHAT_DONE.keySet().removeIf(k -> k.startsWith(needle));
        PLAIN_FULLCAT_LAST_CHAT_MS.remove(playerId);
    }

    /**
     * 在已通过终端上下文等基本校验之后调用；若命中限制则已向客户端发过一条（或静默）并完成顺序队列回执。
     *
     * @return {@code true} 应当立刻中止本条编码且不继续编码逻辑
     */
    public static boolean rejectIfLimited(ServerPlayer player, EncodePatternPacket payload) {
        UUID uuid = player.getUUID();
        int bulkId = payload.bulkEncodeSessionId();

        if (Ae2UtilityServerConfig.blockJeiFullCategoryBatchEncode() && payload.jeiFullCategoryBatch()) {
            if (shouldAnnounceFullCategoryChat(uuid, bulkId)) {
                player.sendSystemMessage(Component.translatable("message.ae2utility.encode_rejected_full_category_blocked_server")
                        .withStyle(ChatFormatting.RED));
            }
            sendRecipeTreeFailureEcho(player, payload);
            return true;
        }

        int max = Ae2UtilityServerConfig.jeiBulkEncodeMaxPatternsPerSession();
        if (bulkId != 0 && max > 0) {
            String ckey = sessionCountKey(uuid, bulkId);
            int n = SESSION_COUNTS.computeIfAbsent(ckey, k -> new AtomicInteger()).incrementAndGet();
            if (n > max) {
                String wkey = overLimitAnnounceKey(uuid, bulkId);
                if (OVER_LIMIT_CHAT_DONE.putIfAbsent(wkey, Boolean.TRUE) == null) {
                    player.sendSystemMessage(Component.translatable("message.ae2utility.bulk_encode_exceeds_server_limit", max)
                            .withStyle(ChatFormatting.GOLD));
                }
                sendRecipeTreeFailureEcho(player, payload);
                return true;
            }
        }
        return false;
    }

    private static void sendRecipeTreeFailureEcho(ServerPlayer player, EncodePatternPacket payload) {
        if (payload.jeiSequentialQueue()) {
            RecipeTreeUploadResultBridge.sendImmediateResult(player, sequentialLabel(payload), false);
        } else {
            RecipeTreeUploadResultBridge.sendImmediateResult(player,
                    payload.patternName().isBlank() ? sequentialLabel(payload) : payload.patternName(), false);
        }
    }

    private static String sequentialLabel(EncodePatternPacket payload) {
        if (!payload.patternName().isBlank()) {
            return payload.patternName();
        }
        return payload.recipeId() != null ? payload.recipeId().toString() : "-";
    }

    private static boolean shouldAnnounceFullCategoryChat(UUID uuid, int bulkId) {
        if (bulkId != 0) {
            String key = uuid + "|" + bulkId + "|fullcat_chat";
            return FULL_CAT_CHAT_DONE.putIfAbsent(key, Boolean.TRUE) == null;
        }
        long now = System.currentTimeMillis();
        Long prev = PLAIN_FULLCAT_LAST_CHAT_MS.put(uuid, now);
        return prev == null || now - prev.longValue() >= PLAIN_FULLCAT_CHAT_GAP_MS;
    }

    /** 用于 {@link com.lhy.ae2utility.service.EncodePatternService#handleBatch} 等整包被拒时只播报一次聊天。*/
    public static void notifyBlockedFullJeCategoryBatch(ServerPlayer player, int bulkSessionHint) {
        if (shouldAnnounceFullCategoryChat(player.getUUID(), bulkSessionHint)) {
            player.sendSystemMessage(Component.translatable("message.ae2utility.encode_rejected_full_category_blocked_server")
                    .withStyle(ChatFormatting.RED));
        }
    }

    private static String sessionCountKey(UUID uuid, int bulkId) {
        return uuid + "|" + bulkId;
    }

    private static String overLimitAnnounceKey(UUID uuid, int bulkId) {
        return uuid + "|" + bulkId + "|over_chat";
    }
}
