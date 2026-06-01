package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.IGrid;

import com.lhy.ae2utility.debug.EaepUploadDebugLog;
import com.lhy.ae2utility.service.RecipeTreeUploadContextBridge;
import com.lhy.ae2utility.service.RecipeTreeUploadResultBridge;

import net.minecraft.server.level.ServerPlayer;

/**
 * 统一对 EAEP {@code CtrlQPendingUploadUtil} 的三个钩子（上传完成结果、取消/归还、findGrid 兜底）。
 */
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
            RecipeTreeUploadResultBridge.flushPendingProviderUiDismissed(player, true);
        } else {
            RecipeTreeUploadResultBridge.flushPendingResult(player, false, true);
        }
    }

    @Inject(method = "findPlayerGrid", at = @At("RETURN"), cancellable = true)
    private static void ae2utility$useRememberedGrid(ServerPlayer player, CallbackInfoReturnable<IGrid> cir) {
        if (cir.getReturnValue() != null) {
            return;
        }
        IGrid rememberedGrid = RecipeTreeUploadContextBridge.getRememberedGrid(player);
        if (rememberedGrid != null) {
            cir.setReturnValue(rememberedGrid);
        }
    }
}
