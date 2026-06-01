package com.lhy.ae2utility.service;

import net.neoforged.fml.ModList;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.debug.EaepUploadDebugLog;

import net.minecraft.server.level.ServerPlayer;

/** 服务端：顺序 Shift + EAEP 供应器界面被关闭但未收到 EAEP mixin 最终结果时的兜底归还。 */
public final class EaepSequentialProviderDismissHandler {
    private EaepSequentialProviderDismissHandler() {
    }

    public static void handle(ServerPlayer player) {
        if (player == null) {
            return;
        }
        EaepUploadDebugLog.info("dismiss handler enter player={} pendingSequential={}",
                player.getScoreboardName(),
                RecipeTreeUploadResultBridge.hasPendingSequentialRecipeTreeResult(player));
        if (!RecipeTreeUploadResultBridge.hasPendingSequentialRecipeTreeResult(player)) {
            return;
        }
        if (!ModList.get().isLoaded("extendedae_plus")) {
            EaepUploadDebugLog.info("dismiss handler no EAEP loaded -> flushPendingResult false/purge");
            RecipeTreeUploadResultBridge.flushPendingResult(player, false, true);
            return;
        }
        EaepUploadDebugLog.info("dismiss handler invoking returnPendingCtrlQPatternToInventory player={}",
                player.getScoreboardName());
        if (!com.lhy.ae2utility.integration.eaep.EaepReflection.returnPendingCtrlQPatternToInventory(player)) {
            EaepUploadDebugLog.warn("dismiss handler invoke returnPending failed player={}",
                    player.getScoreboardName());
            Ae2UtilityMod.LOGGER.warn("EAEP sequential provider dismiss: invoke returnPending failed");
            RecipeTreeUploadResultBridge.flushPendingResult(player, false, true);
        }
    }
}
