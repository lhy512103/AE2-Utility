package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.lhy.ae2utility.debug.EaepUploadDebugLog;
import com.lhy.ae2utility.service.RecipeTreeUploadContextBridge;
import com.lhy.ae2utility.service.RecipeTreeUploadResultBridge;

import net.minecraft.server.level.ServerPlayer;

@Mixin(targets = "com.extendedae_plus.util.uploadPattern.CtrlQPendingUploadUtil", remap = false)
public class MixinCtrlQPendingUploadUtil {
    @Inject(method = "uploadPendingCtrlQPattern", at = @At("RETURN"))
    private static void ae2utility$onUploadPending(ServerPlayer player, long providerId,
            CallbackInfoReturnable<Boolean> cir) {
        boolean ok = cir.getReturnValueZ();
        EaepUploadDebugLog.info(
                "EAEP uploadPendingCtrlQPattern RETURN player={} providerId={} ok={}",
                player != null ? player.getScoreboardName() : "null", providerId, ok);
        if (ok) {
            RecipeTreeUploadContextBridge.rememberSuccessfulProvider(player, providerId);
            RecipeTreeUploadResultBridge.flushPendingResult(player, true);
        } else {
            RecipeTreeUploadResultBridge.flushPendingResult(player, false, true);
        }
    }

    @Inject(method = "returnPendingCtrlQPatternToInventory", at = @At("RETURN"))
    private static void ae2utility$onReturnPending(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        boolean returned = cir.getReturnValueZ();
        EaepUploadDebugLog.info(
                "EAEP returnPendingCtrlQPatternToInventory RETURN player={} returnedOk={}",
                player != null ? player.getScoreboardName() : "null", returned);
        if (returned) {
            // 玩家在供应器界面取消：顺带丢弃客户端队列中与当前条相同 EAEP 检索关键字的待发上传（配方树批量等同机器）
            RecipeTreeUploadResultBridge.flushPendingProviderUiDismissed(player, true);
        } else {
            RecipeTreeUploadResultBridge.flushPendingResult(player, false, true);
        }
    }
}
