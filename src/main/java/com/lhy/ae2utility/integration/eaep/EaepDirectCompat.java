package com.lhy.ae2utility.integration.eaep;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;

import com.lhy.ae2utility.debug.EaepUploadDebugLog;
import com.extendedae_plus.util.uploadPattern.CtrlQPendingUploadUtil;
import com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Direct calls to EAEP's public upload contract. Load this class only after verifying that EAEP is present.
 */
public final class EaepDirectCompat {
    private record ProviderSnapshot(Map<PatternContainer, ItemStack[]> inventories) {
    }

    private static final ConcurrentHashMap<UUID, ProviderSnapshot> PENDING_PROVIDER_SNAPSHOTS =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, IGrid> PREPARED_NETWORK_GRIDS = new ConcurrentHashMap<>();

    private EaepDirectCompat() {
    }

    public static boolean uploadPatternToMatrix(ServerPlayer player, ItemStack pattern, IGrid grid) {
        return ExtendedAEPatternUploadUtil.uploadPatternToMatrix(player, pattern, grid);
    }

    public static boolean uploadInventoryPatternToProvider(ServerPlayer player, int playerSlotIndex, long providerId) {
        return ExtendedAEPatternUploadUtil.uploadPatternToProvider(player, playerSlotIndex, providerId);
    }

    public static boolean preparePatternToNetworkProvider(ServerPlayer player, ItemStack pattern, IGrid grid) {
        if (player == null || pattern.isEmpty() || grid == null) {
            return false;
        }
        clearPreparedNetworkProviderUpload(player);
        if (CtrlQPendingUploadUtil.beginPendingCtrlQUpload(player, pattern.copyWithCount(1)) == null) {
            return false;
        }
        PREPARED_NETWORK_GRIDS.put(player.getUUID(), grid);
        return true;
    }

    public static IGrid findPreparedNetworkProviderGrid(ServerPlayer player) {
        return player == null ? null : PREPARED_NETWORK_GRIDS.get(player.getUUID());
    }

    /** EAEP negative IDs are valid only against the provider ordering used by its prepared pending upload. */
    public static boolean uploadPreparedPatternToNetworkProvider(ServerPlayer player, long providerId) {
        if (player == null || providerId >= 0) {
            return false;
        }
        try {
            return CtrlQPendingUploadUtil.uploadPendingCtrlQPattern(player, providerId);
        } finally {
            CtrlQPendingUploadUtil.clearPendingCtrlQUpload(player);
        }
    }

    public static void clearPreparedNetworkProviderUpload(ServerPlayer player) {
        if (player != null) {
            PREPARED_NETWORK_GRIDS.remove(player.getUUID());
            CtrlQPendingUploadUtil.clearPendingCtrlQUpload(player);
        }
    }

    public static void beginPendingProviderUpload(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        PENDING_PROVIDER_SNAPSHOTS.remove(playerId);
        try {
            IGrid grid = CtrlQPendingUploadUtil.findPlayerGrid(player);
            if (grid == null) {
                return;
            }
            Map<PatternContainer, ItemStack[]> inventories = new IdentityHashMap<>();
            for (PatternContainer container : ExtendedAEPatternUploadUtil.listAvailableProvidersFromGrid(grid)) {
                InternalInventory inventory = container.getTerminalPatternInventory();
                if (inventory != null) {
                    inventories.put(container, snapshot(inventory));
                }
            }
            PENDING_PROVIDER_SNAPSHOTS.put(playerId, new ProviderSnapshot(inventories));
        } catch (Throwable t) {
            PENDING_PROVIDER_SNAPSHOTS.remove(playerId);
            EaepUploadDebugLog.warn("capture pending provider inventories failed player={} error={}",
                    player.getScoreboardName(), t.toString());
        }
    }

    public static void finishPendingProviderUpload(ServerPlayer player, long providerId, boolean success) {
        if (player == null) {
            return;
        }
        ProviderSnapshot snapshot = PENDING_PROVIDER_SNAPSHOTS.remove(player.getUUID());
        if (!success || snapshot == null) {
            return;
        }
        try {
            for (var entry : snapshot.inventories().entrySet()) {
                PatternContainer container = entry.getKey();
                InternalInventory inventory = container.getTerminalPatternInventory();
                int changedSlot = findChangedSlot(inventory, entry.getValue());
                if (changedSlot >= 0) {
                    ExtendedAEPatternUploadUtil.recordProviderUpload(player, providerId, container, changedSlot);
                    return;
                }
            }
        } catch (Throwable t) {
            EaepUploadDebugLog.warn("record pending provider upload failed player={} providerId={} error={}",
                    player.getScoreboardName(), providerId, t.toString());
        }
    }

    public static void clearPendingProviderUpload(ServerPlayer player) {
        if (player != null) {
            PENDING_PROVIDER_SNAPSHOTS.remove(player.getUUID());
        }
    }

    private static ItemStack[] snapshot(InternalInventory inventory) {
        ItemStack[] result = new ItemStack[inventory.size()];
        for (int slot = 0; slot < result.length; slot++) {
            result[slot] = inventory.getStackInSlot(slot).copy();
        }
        return result;
    }

    private static int findChangedSlot(InternalInventory inventory, ItemStack[] before) {
        if (inventory == null || before == null) {
            return -1;
        }
        int size = Math.min(inventory.size(), before.length);
        for (int slot = 0; slot < size; slot++) {
            if (!ItemStack.matches(before[slot], inventory.getStackInSlot(slot))) {
                return slot;
            }
        }
        return -1;
    }
}