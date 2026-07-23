package com.lhy.ae2utility.mixin.eaep;

import appeng.api.networking.IGrid;
import com.lhy.ae2utility.network.CancelJeiBatchEncodeQueuePacket;
import com.lhy.ae2utility.network.ModNetworking;
import com.lhy.ae2utility.service.PendingCraftableRefreshService;
import com.lhy.ae2utility.service.WirelessTerminalContextResolver;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.extendedae_plus.util.uploadPattern.ProviderUploadUtil", remap = false)
public abstract class ProviderUploadUtilMixin {
    @Inject(method = "findPlayerGrid", at = @At("RETURN"), cancellable = true, remap = false)
    private static void ae2utility$resolveWcwtCuriosGrid(ServerPlayer player,
            CallbackInfoReturnable<IGrid> cir) {
        if (cir.getReturnValue() != null || player == null) {
            return;
        }

        var resolution = WirelessTerminalContextResolver.resolve(player);
        if (!resolution.isReady()) {
            return;
        }

        var node = resolution.host().getActionableNode();
        if (node != null && node.getGrid() != null) {
            cir.setReturnValue(node.getGrid());
        }
    }

    @Inject(method = "uploadPendingCtrlQPattern", at = @At("RETURN"), remap = false)
    private static void ae2utility$refreshCraftableCache(ServerPlayer player, long providerId,
            CallbackInfoReturnable<Boolean> cir) {
        PendingCraftableRefreshService.flush(player, cir.getReturnValueZ());
    }

    @Inject(method = "returnPendingCtrlQPatternToInventory", at = @At("HEAD"), remap = false)
    private static void ae2utility$clearCraftableRefresh(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        PendingCraftableRefreshService.clear(player);
    }

    @Inject(method = "returnPendingCtrlQPatternToInventory", at = @At("RETURN"), remap = false)
    private static void ae2utility$cancelJeiBatchQueue(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (player != null && cir.getReturnValueZ()) {
            ModNetworking.sendToPlayer(player, CancelJeiBatchEncodeQueuePacket.INSTANCE);
        }
    }
}
