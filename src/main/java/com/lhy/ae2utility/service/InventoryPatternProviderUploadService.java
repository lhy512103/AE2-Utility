package com.lhy.ae2utility.service;

import com.lhy.ae2utility.debug.EaepUploadDebugLog;
import com.lhy.ae2utility.debug.InventoryPatternUploadDebug;
import com.lhy.ae2utility.integration.eaep.EaepDirectCompat;
import com.lhy.ae2utility.integration.eaep.EaepReflection;
import com.lhy.ae2utility.network.InventoryProviderSelectionPreparedPacket;
import com.lhy.ae2utility.network.InventoryProviderUploadAckPacket;
import com.lhy.ae2utility.network.PrepareInventoryProviderSelectionPacket;
import com.lhy.ae2utility.network.UploadInventoryPatternToProviderPacket;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class InventoryPatternProviderUploadService {
    private InventoryPatternProviderUploadService() {
    }

    public static void prepareSelection(ServerPlayer player, PrepareInventoryProviderSelectionPacket payload) {
        boolean success = false;
        try {
            if (player == null || payload == null || !EaepReflection.isLoaded()) {
                return;
            }
            int slotIndex = payload.playerSlotIndex();
            if (slotIndex < 0) {
                EaepDirectCompat.clearPreparedNetworkProviderUpload(player);
                success = true;
                return;
            }
            if (slotIndex >= player.getInventory().getContainerSize()) {
                return;
            }
            ItemStack stack = player.getInventory().getItem(slotIndex);
            if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
                return;
            }
            IGrid grid = InventoryPatternUploadGridResolver.resolve(player);
            if (grid == null) {
                InventoryPatternUploadDebug.warn("prepare_provider_selection", "no grid resolved slot={}", slotIndex);
                return;
            }
            success = EaepDirectCompat.preparePatternToNetworkProvider(player, stack, grid);
        } finally {
            if (player != null && payload != null && payload.playerSlotIndex() >= 0) {
                PacketDistributor.sendToPlayer(player, new InventoryProviderSelectionPreparedPacket(
                        payload.playerSlotIndex(), success));
            }
        }
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

            boolean networkProvider = payload.providerId() < 0;
            boolean uploaded = EaepReflection.isLoaded()
                    && (networkProvider
                            ? EaepDirectCompat.uploadPreparedPatternToNetworkProvider(player, payload.providerId())
                            : EaepDirectCompat.uploadInventoryPatternToProvider(player, slotIndex, payload.providerId()));
            EaepUploadDebugLog.info("UploadInventoryPatternToProvider direct upload returned={} networkProvider={}",
                    uploaded, networkProvider);
            if (!uploaded) {
                InventoryPatternUploadDebug.warn("provider_upload", "upload failed slot={} providerId={}", slotIndex,
                        payload.providerId());
                return;
            }

            if (networkProvider) {
                stack.shrink(1);
                player.getInventory().setItem(slotIndex, stack.isEmpty() ? ItemStack.EMPTY : stack);
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            ItemStack remaining = player.getInventory().getItem(slotIndex);
            InventoryPatternUploadDebug.info("provider_upload", "uploaded slot={} providerId={} remaining={}", slotIndex,
                    payload.providerId(), remaining.getCount());
            success = true;
        } catch (Throwable t) {
            InventoryPatternUploadDebug.warn("provider_upload", "exception slot={} providerId={} error={}",
                    slotIndex, payload.providerId(), t.toString());
        } finally {
            PacketDistributor.sendToPlayer(player, new InventoryProviderUploadAckPacket(slotIndex, success));
        }
    }
}
