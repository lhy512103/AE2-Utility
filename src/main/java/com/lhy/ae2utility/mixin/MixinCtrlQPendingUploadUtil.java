package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import appeng.api.networking.IGrid;

import com.lhy.ae2utility.debug.EaepUploadDebugLog;
import com.lhy.ae2utility.integration.eaep.EaepDirectCompat;
import com.lhy.ae2utility.service.SequentialUploadContextBridge;
import com.lhy.ae2utility.service.SequentialUploadResultBridge;

import net.minecraft.server.level.ServerPlayer;

/**
 * 统一对 EAEP {@code CtrlQPendingUploadUtil} 的三个钩子（上传完成结果、取消/归还、findGrid 兜底）。
 */
@Mixin(targets = "com.extendedae_plus.util.uploadPattern.CtrlQPendingUploadUtil", remap = false)
public class MixinCtrlQPendingUploadUtil {

    @WrapMethod(method = "uploadPendingCtrlQPattern")
    private static boolean ae2utility$recordPendingUploadTarget(ServerPlayer player, long providerId,
            Operation<Boolean> original) {
        EaepDirectCompat.clearPendingProviderUpload(player);
        try {
            EaepDirectCompat.beginPendingProviderUpload(player);
            boolean ok = original.call(player, providerId);
            EaepDirectCompat.finishPendingProviderUpload(player, providerId, ok);
            EaepUploadDebugLog.info(
                    "EAEP uploadPendingCtrlQPattern RETURN player={} providerId={} ok={}",
                    player != null ? player.getScoreboardName() : "null", providerId, ok);
            if (ok) {
                SequentialUploadContextBridge.rememberSuccessfulProvider(player, providerId);
                SequentialUploadResultBridge.flushPendingResult(player, true);
            } else {
                SequentialUploadResultBridge.flushPendingResult(player, false, true);
            }
            return ok;
        } finally {
            EaepDirectCompat.clearPendingProviderUpload(player);
        }
    }

    @Inject(method = "returnPendingCtrlQPatternToInventory", at = @At("RETURN"))
    private static void ae2utility$onReturnPending(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        boolean returned = cir.getReturnValueZ();
        EaepUploadDebugLog.info(
                "EAEP returnPendingCtrlQPatternToInventory RETURN player={} returnedOk={}",
                player != null ? player.getScoreboardName() : "null", returned);
        if (returned) {
            SequentialUploadResultBridge.flushPendingProviderUiDismissed(player, true);
        } else {
            SequentialUploadResultBridge.flushPendingResult(player, false, true);
        }
    }

    @Inject(method = "findPlayerGrid", at = @At("RETURN"), cancellable = true)
    private static void ae2utility$useRememberedGrid(ServerPlayer player, CallbackInfoReturnable<IGrid> cir) {
        IGrid preparedGrid = EaepDirectCompat.findPreparedNetworkProviderGrid(player);
        if (preparedGrid != null) {
            cir.setReturnValue(preparedGrid);
            return;
        }
        if (cir.getReturnValue() != null) {
            return;
        }
        IGrid rememberedGrid = SequentialUploadContextBridge.getRememberedGrid(player);
        if (rememberedGrid != null) {
            cir.setReturnValue(rememberedGrid);
        }
    }
}
