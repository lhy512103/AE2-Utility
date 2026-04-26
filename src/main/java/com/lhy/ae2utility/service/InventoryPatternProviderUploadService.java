package com.lhy.ae2utility.service;

import java.lang.reflect.Method;

import com.lhy.ae2utility.debug.InventoryPatternUploadDebug;
import com.lhy.ae2utility.network.UploadInventoryPatternToProviderPacket;

import appeng.api.crafting.PatternDetailsHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class InventoryPatternProviderUploadService {
    private InventoryPatternProviderUploadService() {
    }

    public static void handle(ServerPlayer player, UploadInventoryPatternToProviderPacket payload) {
        if (player == null || payload == null) {
            return;
        }

        int slotIndex = payload.playerSlotIndex();
        if (slotIndex < 0 || slotIndex >= player.getInventory().getContainerSize()) {
            InventoryPatternUploadDebug.warn("provider_upload", "invalid slotIndex={} providerId={}", slotIndex, payload.providerId());
            return;
        }

        ItemStack stack = player.getInventory().getItem(slotIndex);
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            InventoryPatternUploadDebug.warn("provider_upload", "slot={} emptyOrNotPattern providerId={}", slotIndex, payload.providerId());
            return;
        }

        ItemStack singlePattern = stack.copyWithCount(1);

        try {
            Class<?> pendingUtil = Class.forName("com.extendedae_plus.util.uploadPattern.CtrlQPendingUploadUtil");
            Method clearPending = pendingUtil.getMethod("clearPendingCtrlQUpload", ServerPlayer.class);
            Method beginPending = pendingUtil.getMethod("beginPendingCtrlQUpload", ServerPlayer.class, ItemStack.class);
            Method uploadPending = pendingUtil.getMethod("uploadPendingCtrlQPattern", ServerPlayer.class, long.class);

            clearPending.invoke(null, player);
            beginPending.invoke(null, player, singlePattern);

            boolean uploaded = (Boolean) uploadPending.invoke(null, player, payload.providerId());
            if (!uploaded) {
                clearPending.invoke(null, player);
                InventoryPatternUploadDebug.warn("provider_upload", "upload failed slot={} providerId={}", slotIndex, payload.providerId());
                return;
            }

            stack.shrink(1);
            player.getInventory().setItem(slotIndex, stack.isEmpty() ? ItemStack.EMPTY : stack);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            InventoryPatternUploadDebug.info("provider_upload", "uploaded slot={} providerId={} remaining={}", slotIndex, payload.providerId(), stack.getCount());
        } catch (Throwable t) {
            InventoryPatternUploadDebug.warn("provider_upload", "exception slot={} providerId={} error={}",
                    slotIndex, payload.providerId(), t.toString());
        }
    }
}
