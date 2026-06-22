package com.lhy.ae2utility.mixin.eaep;

import com.lhy.ae2utility.service.PendingCraftableRefreshService;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.extendedae_plus.util.uploadPattern.ProviderUploadUtil", remap = false)
public abstract class ProviderUploadUtilMixin {
    @Inject(method = "uploadPendingCtrlQPattern", at = @At("RETURN"), remap = false)
    private static void ae2utility$refreshCraftableCache(ServerPlayer player, long providerId,
            CallbackInfoReturnable<Boolean> cir) {
        PendingCraftableRefreshService.flush(player, cir.getReturnValueZ());
    }

    @Inject(method = "returnPendingCtrlQPatternToInventory", at = @At("HEAD"), remap = false)
    private static void ae2utility$clearCraftableRefresh(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        PendingCraftableRefreshService.clear(player);
    }
}
