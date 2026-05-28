package com.lhy.ae2utility.service;

import net.neoforged.fml.ModList;

import com.lhy.ae2utility.Ae2UtilityMod;

import net.minecraft.server.level.ServerPlayer;

/** 服务端：顺序 Shift + EAEP 供应器界面被关闭但未收到 EAEP mixin 最终结果时的兜底归还。 */
public final class EaepSequentialProviderDismissHandler {
    private EaepSequentialProviderDismissHandler() {
    }

    public static void handle(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (!RecipeTreeUploadResultBridge.hasPendingSequentialRecipeTreeResult(player)) {
            return;
        }
        if (!ModList.get().isLoaded("extendedae_plus")) {
            RecipeTreeUploadResultBridge.flushPendingResult(player, false, true);
            return;
        }
        try {
            Class<?> pendingUtil =
                    Class.forName("com.extendedae_plus.util.uploadPattern.CtrlQPendingUploadUtil");
            java.lang.reflect.Method ret =
                    pendingUtil.getMethod("returnPendingCtrlQPatternToInventory", ServerPlayer.class);
            ret.invoke(null, player);
        } catch (Throwable t) {
            Ae2UtilityMod.LOGGER.warn("EAEP sequential provider dismiss: invoke returnPending failed: {}", t.toString());
            RecipeTreeUploadResultBridge.flushPendingResult(player, false, true);
        }
    }
}
