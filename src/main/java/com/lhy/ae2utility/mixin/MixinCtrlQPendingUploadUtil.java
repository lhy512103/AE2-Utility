package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.extendedae_plus.util.uploadPattern.ProviderUploadUtil.UploadOutcome;
import com.lhy.ae2utility.service.InventoryPatternProviderUploadService;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;




import appeng.api.networking.IGrid;

import com.lhy.ae2utility.debug.EaepUploadDebugLog;
import com.lhy.ae2utility.integration.eaep.EaepDirectCompat;
import com.lhy.ae2utility.service.SequentialUploadContextBridge;
import com.lhy.ae2utility.service.SequentialUploadResultBridge;

import net.minecraft.server.level.ServerPlayer;

/**
 * 统一对 EAEP {@code CtrlQPendingUploadUtil} 的三个钩子（上传完成结果、取消/归还、findGrid 兜底）。
 */
@Mixin(targets = "com.extendedae_plus.util.uploadPattern.ProviderUploadUtil", remap = false)
public class MixinCtrlQPendingUploadUtil {

    @Shadow
    private static ItemStack getPendingCtrlQPattern(ServerPlayer player) {
        throw new AssertionError();
    }

    @Inject(method = "uploadPendingCtrlQPattern", at = @At("HEAD"), cancellable = true)
    private static void ae2utility$beginUpload(ServerPlayer player, long providerId, CallbackInfoReturnable<UploadOutcome> cir) {
        if (!InventoryPatternProviderUploadService.authorizePendingUpload(player, getPendingCtrlQPattern(player))) {
            cir.setReturnValue(UploadOutcome.FAILED);
            return;
        }
        EaepDirectCompat.beginPendingProviderUpload(player);
    }

    // The pending copy contains exactly one item. EAEP reaches this clear only after
    // successful insertion, before its normal clear hook revokes the inventory binding.
    @Inject(method = "uploadPendingCtrlQPattern", at = @At(value = "INVOKE",
            target = "Lcom/extendedae_plus/util/uploadPattern/ProviderUploadUtil;clearPendingCtrlQUpload(Lnet/minecraft/server/level/ServerPlayer;)V"))
    private static void ae2utility$consumeUploadedInventoryPattern(ServerPlayer player, long providerId,
            CallbackInfoReturnable<UploadOutcome> cir) {
        InventoryPatternProviderUploadService.consumeAuthorizedPendingUpload(player);
    }

    @Inject(method = "clearPendingCtrlQUpload", at = @At("HEAD"))
    private static void ae2utility$forgetPreparedSelection(ServerPlayer player,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        InventoryPatternProviderUploadService.forgetPreparedSelection(player);
        EaepDirectCompat.forgetPreparedNetworkProviderGrid(player);
    }

    @Inject(method = "returnPendingCtrlQPatternToInventory", at = @At("HEAD"), cancellable = true)
    private static void ae2utility$discardBorrowedCopy(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (InventoryPatternProviderUploadService.discardBorrowedPending(player)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "uploadPendingCtrlQPattern", at = @At("RETURN"))
    private static void ae2utility$finishUpload(ServerPlayer player, long providerId, CallbackInfoReturnable<UploadOutcome> cir) {
        boolean ok = cir.getReturnValue() == UploadOutcome.SUCCESS;
        if (!ok) {
            InventoryPatternProviderUploadService.discardBorrowedPending(player);
        }
        try {
            EaepDirectCompat.finishPendingProviderUpload(player, providerId, ok);
            if (ok) {
                SequentialUploadContextBridge.rememberSuccessfulProvider(player, providerId);
                SequentialUploadResultBridge.flushPendingResult(player, true);
            } else {
                SequentialUploadResultBridge.flushPendingResult(player, false, true);
            }
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
