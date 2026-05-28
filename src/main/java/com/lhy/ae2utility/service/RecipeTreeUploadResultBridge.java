package com.lhy.ae2utility.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.nbt.Tag;

import appeng.api.stacks.AEKey;

import com.lhy.ae2utility.debug.EaepUploadDebugLog;
import com.lhy.ae2utility.network.InvalidateCraftableCachePacket;
import com.lhy.ae2utility.network.RecipeTreeUploadResultPacket;

public final class RecipeTreeUploadResultBridge {
    private static final String PENDING_NAME_KEY = "ae2utility_recipe_tree_pending_name";
    private static final Map<UUID, List<AEKey>> PENDING_CRAFTABLE_REFRESH_KEYS = new ConcurrentHashMap<>();

    private RecipeTreeUploadResultBridge() {
    }

    public static void rememberPendingName(ServerPlayer player, String patternName) {
        if (player == null) {
            return;
        }
        player.getPersistentData().putString(PENDING_NAME_KEY,
                patternName == null || patternName.isBlank() ? "-" : patternName);
    }

    public static void rememberPendingCraftableRefresh(ServerPlayer player, List<AEKey> keys) {
        if (player == null || keys == null || keys.isEmpty()) {
            return;
        }
        PENDING_CRAFTABLE_REFRESH_KEYS.put(player.getUUID(), List.copyOf(keys));
    }

    private static List<AEKey> takePendingCraftableRefreshKeys(ServerPlayer player) {
        if (player == null) {
            return List.of();
        }
        List<AEKey> keys = PENDING_CRAFTABLE_REFRESH_KEYS.remove(player.getUUID());
        return keys == null ? List.of() : keys;
    }

    public static void clearPendingName(ServerPlayer player) {
        if (player != null) {
            player.getPersistentData().remove(PENDING_NAME_KEY);
            RecipeTreeUploadContextBridge.clear(player);
            PENDING_CRAFTABLE_REFRESH_KEYS.remove(player.getUUID());
        }
    }

    /**
     * 管理端中止或玩家执行 stopuploads 时调用：抹去待下发的 EAEP 供应器最终结果与完整编排上下文（含按关键字记忆的供应器 id）。
     */
    public static void abortServerUploadOrchestration(ServerPlayer player) {
        if (player != null) {
            player.getPersistentData().remove(PENDING_NAME_KEY);
            PENDING_CRAFTABLE_REFRESH_KEYS.remove(player.getUUID());
            EncodePatternService.disarmEaepShiftBlankRefund(player);
        }
        RecipeTreeUploadContextBridge.wipeUploadOrchestrationState(player);
    }

    /** 是否存在「等待玩家在 EAEP 界面完成 CtrlQ / 样板归还」的顺序上传会话标记。 */
    public static boolean hasPendingSequentialRecipeTreeResult(ServerPlayer player) {
        return player != null && player.getPersistentData().contains(PENDING_NAME_KEY, Tag.TAG_STRING);
    }

    public static void sendImmediateResult(ServerPlayer player, String patternName, boolean uploaded) {
        sendImmediateResult(player, patternName, uploaded, false, false, false);
    }

    public static void sendImmediateResult(ServerPlayer player, String patternName, boolean uploaded, boolean abortRemainingBatch) {
        sendImmediateResult(player, patternName, uploaded, abortRemainingBatch, false, false);
    }

    /**
     * @param purgeQueuedSameEaepMachine 客户端：顺带丢弃队列中与当前条相同 EAEP 供应器关键字的未完成上传（常用于供应器满/拒绝写入）。
     */
    public static void sendImmediateResult(ServerPlayer player, String patternName, boolean uploaded,
            boolean abortRemainingBatch, boolean purgeQueuedSameEaepMachine) {
        sendImmediateResult(player, patternName, uploaded, abortRemainingBatch, purgeQueuedSameEaepMachine, false);
    }

    public static void sendImmediateResult(ServerPlayer player, String patternName, boolean uploaded,
            boolean abortRemainingBatch, boolean purgeQueuedSameEaepMachine, boolean missingBlankPatternFailure) {
        if (player == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new RecipeTreeUploadResultPacket(
                patternName != null ? patternName : "", uploaded, false, abortRemainingBatch, purgeQueuedSameEaepMachine,
                missingBlankPatternFailure));
    }

    /**
     * JEI 队列需在收到本条之前冻结：否则会早于供应器上传完成就发送下一条编码包。
     */
    public static void sendAwaitingProviderUpload(ServerPlayer player, String patternName) {
        if (player == null) {
            return;
        }
        String name = patternName != null ? patternName : "";
        PacketDistributor.sendToPlayer(player, new RecipeTreeUploadResultPacket(name, false, true, false, false, false));
    }

    /**
     * EAEP 已将待上传编码样板退回背包：不再退还空白样板（避免已与编码样板重复）。
     */
    public static void flushPendingProviderUiDismissed(ServerPlayer player, boolean purgeQueuedSameEaepMachine) {
        if (player == null) {
            return;
        }
        String patternName = player.getPersistentData().getString(PENDING_NAME_KEY);
        if (patternName == null || patternName.isBlank()) {
            patternName = "-";
        }
        EaepUploadDebugLog.info(
                "flushPendingProviderUiDismissed player={} patternName={} purgeQueuedSameEaep={}",
                player.getScoreboardName(), patternName, purgeQueuedSameEaepMachine);
        EncodePatternService.disarmEaepShiftBlankRefund(player);
        clearPendingName(player);
        PacketDistributor.sendToPlayer(player,
                new RecipeTreeUploadResultPacket(patternName, false, false, false, purgeQueuedSameEaepMachine, false));
    }

    public static void flushPendingResult(ServerPlayer player, boolean uploaded) {
        flushPendingResult(player, uploaded, false);
    }

    public static void flushPendingResult(ServerPlayer player, boolean uploaded, boolean purgeQueuedSameEaepMachine) {
        if (player == null) {
            return;
        }
        String patternName = player.getPersistentData().getString(PENDING_NAME_KEY);
        if (patternName == null || patternName.isBlank()) {
            patternName = "-";
        }
        EaepUploadDebugLog.info(
                "flushPendingResult player={} patternName={} uploaded={} purgeQueuedSameEaep={}",
                player.getScoreboardName(), patternName, uploaded, purgeQueuedSameEaepMachine);
        if (uploaded) {
            EncodePatternService.disarmEaepShiftBlankRefund(player);
        } else {
            EncodePatternService.refundEaepShiftBlankIfPending(player);
        }
        List<AEKey> craftableRefresh = takePendingCraftableRefreshKeys(player);
        clearPendingName(player);
        if (uploaded && !craftableRefresh.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new InvalidateCraftableCachePacket(craftableRefresh));
        }
        PacketDistributor.sendToPlayer(player,
                new RecipeTreeUploadResultPacket(patternName, uploaded, false, false, purgeQueuedSameEaepMachine, false));
    }
}
