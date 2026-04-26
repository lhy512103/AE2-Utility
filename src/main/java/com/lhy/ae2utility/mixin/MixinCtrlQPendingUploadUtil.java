package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.lhy.ae2utility.service.RecipeTreeUploadResultBridge;

import net.minecraft.server.level.ServerPlayer;

@Mixin(targets = "com.extendedae_plus.util.uploadPattern.CtrlQPendingUploadUtil", remap = false)
public class MixinCtrlQPendingUploadUtil {
    @Inject(method = "uploadPendingCtrlQPattern", at = @At("RETURN"))
    private static void ae2utility$onUploadPending(ServerPlayer player, long providerId,
            CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            RecipeTreeUploadResultBridge.flushPendingResult(player, true);
        }
    }

    @Inject(method = "returnPendingCtrlQPatternToInventory", at = @At("RETURN"))
    private static void ae2utility$onReturnPending(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            RecipeTreeUploadResultBridge.flushPendingResult(player, false);
        }
    }
}
