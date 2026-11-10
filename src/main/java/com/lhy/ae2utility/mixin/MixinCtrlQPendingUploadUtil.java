package com.lhy.ae2utility.mixin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.IGrid;

import com.lhy.ae2utility.debug.EaepUploadDebugLog;
import com.lhy.ae2utility.integration.eaep.EaepApiAccess;
import com.lhy.ae2utility.integration.eaep.EaepApiAccess.UploadResult;
import com.lhy.ae2utility.integration.eaep.EaepDirectCompat;
import com.lhy.ae2utility.service.SequentialUploadContextBridge;
import com.lhy.ae2utility.service.SequentialUploadResultBridge;

import net.minecraft.server.level.ServerPlayer;

/** Upload hooks accept both legacy boolean and current UploadOutcome without EAEP class linkage. */
@org.spongepowered.asm.mixin.Pseudo
@Mixin(targets = "com.extendedae_plus.util.uploadPattern.CtrlQPendingUploadUtil", remap = false)
public class MixinCtrlQPendingUploadUtil {
    @Unique
    private static final Map<UUID, UploadResult> ae2utility$awaitingPatternReturn = new ConcurrentHashMap<>();

    @Inject(method = "uploadPendingCtrlQPattern", at = @At("HEAD"))
    private static void ae2utility$capturePendingUploadTarget(ServerPlayer player, long providerId,
            CallbackInfoReturnable<Boolean> cir) {
        if (player != null) {
            ae2utility$awaitingPatternReturn.remove(player.getUUID());
        }
        EaepDirectCompat.clearPendingProviderUpload(player);
        EaepDirectCompat.beginPendingProviderUpload(player);
    }

    @Inject(method = "uploadPendingCtrlQPattern", at = @At("RETURN"))
    private static void ae2utility$recordPendingUploadTarget(ServerPlayer player, long providerId,
            CallbackInfoReturnable<Boolean> cir) {
        UploadResult result = EaepApiAccess.uploadResult(cir.getReturnValue());
        boolean inserted = result == UploadResult.SUCCESS;
        try {
            EaepDirectCompat.finishPendingProviderUpload(player, providerId, inserted);
            EaepUploadDebugLog.info("EAEP uploadPendingCtrlQPattern RETURN player={} providerId={} outcome={}",
                    player != null ? player.getScoreboardName() : "null", providerId, result);
            if (!SequentialUploadResultBridge.hasPendingSequentialUploadResult(player)) {
                // Inventory uploads use a copied pending stack and their own acknowledgement packet.
                return;
            }
            if (inserted) {
                SequentialUploadContextBridge.rememberSuccessfulProvider(player, providerId);
                SequentialUploadResultBridge.flushPendingResult(player, true);
            } else if (player != null) {
                // EAEP returns the encoded pending stack for DUPLICATE/FAILED. Do not refund a
                // blank here as well: wait for that return, then disarm the blank refund exactly once.
                ae2utility$awaitingPatternReturn.put(player.getUUID(), result);
            }
        } finally {
            EaepDirectCompat.clearPendingProviderUpload(player);
        }
    }

    @Inject(method = "returnPendingCtrlQPatternToInventory", at = @At("RETURN"))
    private static void ae2utility$onReturnPending(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        UploadResult uploadResult = player == null ? null
                : ae2utility$awaitingPatternReturn.remove(player.getUUID());
        if (!SequentialUploadResultBridge.hasPendingSequentialUploadResult(player)) {
            return;
        }
        boolean returned = cir.getReturnValueZ();
        boolean purgeSameMachine = uploadResult != UploadResult.DUPLICATE;
        EaepUploadDebugLog.info("EAEP returnPendingCtrlQPatternToInventory player={} returned={} outcome={}",
                player != null ? player.getScoreboardName() : "null", returned, uploadResult);
        if (returned) {
            SequentialUploadResultBridge.flushPendingProviderUiDismissed(player, purgeSameMachine);
        } else {
            SequentialUploadResultBridge.flushPendingResult(player, false, purgeSameMachine);
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
