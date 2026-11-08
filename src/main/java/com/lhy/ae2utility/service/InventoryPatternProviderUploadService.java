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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import com.lhy.ae2utility.network.ModNetworking;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.lhy.ae2utility.Ae2UtilityMod.MOD_ID)
public final class InventoryPatternProviderUploadService {
    private static final String BORROWED_PENDING = "ae2utility_inventory_upload_borrowed_pending";
    private static final PreparedInventoryPatternUploads PREPARED = new PreparedInventoryPatternUploads();

    private InventoryPatternProviderUploadService() {
    }

    public static void prepareSelection(ServerPlayer player, PrepareInventoryProviderSelectionPacket payload) {
        boolean success = false;
        try {
            if (player == null || payload == null || !EaepReflection.isLoaded()) {
                return;
            }
            if (!PreparedInventoryPatternUploads.canReplacePending(EaepDirectCompat.hasPendingPattern(player),
                    player.getPersistentData().getBoolean(BORROWED_PENDING))) {
                InventoryPatternUploadDebug.warn("prepare_provider_selection", "kept existing owned pending pattern");
                return;
            }
            EaepDirectCompat.clearPreparedNetworkProviderUpload(player);
            int slotIndex = payload.playerSlotIndex();
            if (slotIndex < 0) {
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
            if (success) {
                PREPARED.prepare(player.getUUID(), slotIndex, identity(stack));
                // EAEP persists its pending stack. Persist ownership too, so after a restart
                // a borrowed copy cannot be uploaded/returned without its in-memory authorization.
                player.getPersistentData().putBoolean(BORROWED_PENDING, true);
            }
        } finally {
            if (player != null && payload != null && payload.playerSlotIndex() >= 0) {
                ModNetworking.sendToPlayer(player, new InventoryProviderSelectionPreparedPacket(
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
                return;
            }
            ItemStack stack = player.getInventory().getItem(slotIndex);
            if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
                return;
            }
            boolean networkProvider = payload.providerId() < 0;
            if (networkProvider && !PREPARED.matches(player.getUUID(), slotIndex, identity(stack))) {
                if (EaepReflection.isLoaded()) {
                    discardBorrowedPending(player);
                }
                return;
            }
            boolean uploaded = EaepReflection.isLoaded()
                    && (networkProvider
                            ? EaepDirectCompat.uploadPreparedPatternToNetworkProvider(player, payload.providerId())
                            : EaepDirectCompat.uploadInventoryPatternToProvider(player, slotIndex, payload.providerId()));
            EaepUploadDebugLog.info("UploadInventoryPatternToProvider direct upload returned={} networkProvider={}",
                    uploaded, networkProvider);
            // Both native EAEP packets and our packet pass the same guarded utility hook.
            // That hook consumes the bound slot only on SUCCESS; do not shrink twice here.
            if (uploaded) {
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
                success = true;
            }
        } catch (Throwable t) {
            InventoryPatternUploadDebug.warn("provider_upload", "exception slot={} providerId={} error={}",
                    slotIndex, payload.providerId(), t.toString());
        } finally {
            ModNetworking.sendToPlayer(player, new InventoryProviderUploadAckPacket(slotIndex, success));
        }
    }

    /** Called at the common EAEP utility entry, including packets sent on EAEP's own channel. */
    public static boolean authorizePendingUpload(ServerPlayer player, ItemStack pending) {
        if (player == null || !player.getPersistentData().getBoolean(BORROWED_PENDING)) {
            return true; // Genuine EAEP/encoding pending stacks already own their item.
        }
        int slot = PREPARED.slot(player.getUUID());
        if (slot < 0 || slot >= player.getInventory().getContainerSize()
                || pending.getCount() != 1
                || !PREPARED.matches(player.getUUID(), slot, identity(pending))
                || !PREPARED.matches(player.getUUID(), slot, identity(player.getInventory().getItem(slot)))) {
            EaepDirectCompat.clearPreparedNetworkProviderUpload(player);
            return false;
        }
        return true;
    }

    /** Runs before EAEP may clear pending data, while the original slot is still authorized. */
    public static void consumeAuthorizedPendingUpload(ServerPlayer player) {
        if (player == null || !player.getPersistentData().getBoolean(BORROWED_PENDING)) {
            return;
        }
        int slot = PREPARED.slot(player.getUUID());
        ItemStack stack = player.getInventory().getItem(slot);
        stack.shrink(1);
        player.getInventory().setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    /** Cancelling a borrowed copy must never return another physical pattern to the inventory. */
    public static boolean discardBorrowedPending(ServerPlayer player) {
        if (player == null || !player.getPersistentData().getBoolean(BORROWED_PENDING)) {
            return false;
        }
        EaepDirectCompat.clearPreparedNetworkProviderUpload(player);
        return true;
    }

    /** Invoked by EAEP's clearPending hook, including cancellation/replacement by native callers. */
    public static void forgetPreparedSelection(ServerPlayer player) {
        if (player != null) {
            PREPARED.clear(player.getUUID());
            player.getPersistentData().remove(BORROWED_PENDING);
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onPlayerLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && EaepReflection.isLoaded()) {
            discardBorrowedPending(player);
            forgetPreparedSelection(player);
        }
    }

    private static CompoundTag identity(ItemStack stack) {
        return stack.copyWithCount(1).save(new CompoundTag());
    }
}
