package com.lhy.ae2utility.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import appeng.api.stacks.AEKey;
import com.lhy.ae2utility.network.CraftableStatePacket;
import com.lhy.ae2utility.network.ModNetworking;
import net.minecraft.server.level.ServerPlayer;

public final class PendingCraftableRefreshService {
    private static final ConcurrentMap<UUID, List<AEKey>> PENDING_KEYS = new ConcurrentHashMap<>();

    private PendingCraftableRefreshService() {
    }

    public static void remember(ServerPlayer player, List<AEKey> keys) {
        if (player == null || keys == null || keys.isEmpty()) {
            return;
        }
        PENDING_KEYS.put(player.getUUID(), List.copyOf(keys));
    }

    public static void clear(ServerPlayer player) {
        if (player != null) {
            PENDING_KEYS.remove(player.getUUID());
        }
    }

    public static void flush(ServerPlayer player, boolean uploaded) {
        if (player == null) {
            return;
        }
        List<AEKey> keys = PENDING_KEYS.remove(player.getUUID());
        if (uploaded && keys != null && !keys.isEmpty()) {
            ModNetworking.sendToPlayer(player, new CraftableStatePacket(new ArrayList<>(keys), List.of()));
        }
    }
}
