package com.lhy.ae2utility.service;

import java.lang.reflect.Method;

import com.lhy.ae2utility.debug.EaepUploadDebugLog;
import com.lhy.ae2utility.debug.InventoryPatternUploadDebug;
import com.lhy.ae2utility.network.InventoryProviderUploadAckPacket;
import com.lhy.ae2utility.network.UploadInventoryPatternToProviderPacket;

import appeng.api.crafting.PatternDetailsHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class InventoryPatternProviderUploadService {
    private InventoryPatternProviderUploadService() {
    }

    public static void handle(ServerPlayer player, UploadInventoryPatternToProviderPacket payload) {
        if (player == null || payload == null) {
            return;
        }

        int slotIndex = payload.playerSlotIndex();
        boolean success = false;
        try {
            if (slotIndex < 0 || slotIndex >= player.getInventory().getContainerSize()) {
                InventoryPatternUploadDebug.warn("provider_upload", "invalid slotIndex={} providerId={}", slotIndex, payload.providerId());
                return;
            }

            ItemStack stack = player.getInventory().getItem(slotIndex);
            if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
                InventoryPatternUploadDebug.warn("provider_upload", "slot={} emptyOrNotPattern providerId={}", slotIndex,
                        payload.providerId());
                return;
            }

            ItemStack singlePattern = stack.copyWithCount(1);

            com.lhy.ae2utility.integration.eaep.EaepReflection.clearPendingCtrlQUpload(player);
            com.lhy.ae2utility.integration.eaep.EaepReflection.beginPendingCtrlQUpload(player, singlePattern);

            boolean uploaded = com.lhy.ae2utility.integration.eaep.EaepReflection
                    .uploadPendingCtrlQPattern(player, payload.providerId());
            EaepUploadDebugLog.info("UploadInventoryPatternToProvider uploadPendingCtrlQPattern returned={}", uploaded);
            if (!uploaded) {
                com.lhy.ae2utility.integration.eaep.EaepReflection.clearPendingCtrlQUpload(player);
                InventoryPatternUploadDebug.warn("provider_upload", "upload failed slot={} providerId={}", slotIndex, payload.providerId());
                return;
            }

            stack.shrink(1);
            player.getInventory().setItem(slotIndex, stack.isEmpty() ? ItemStack.EMPTY : stack);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            InventoryPatternUploadDebug.info("provider_upload", "uploaded slot={} providerId={} remaining={}", slotIndex, payload.providerId(),
                    stack.getCount());
            success = true;
        } catch (Throwable t) {
            InventoryPatternUploadDebug.warn("provider_upload", "exception slot={} providerId={} error={}",
                    slotIndex, payload.providerId(), t.toString());
        } finally {
            PacketDistributor.sendToPlayer(player, new InventoryProviderUploadAckPacket(slotIndex, success));
        }
    }
}
