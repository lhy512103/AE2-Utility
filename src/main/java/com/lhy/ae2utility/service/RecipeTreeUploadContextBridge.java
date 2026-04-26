package com.lhy.ae2utility.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import appeng.api.networking.IGrid;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public final class RecipeTreeUploadContextBridge {
    private static final Map<UUID, IGrid> PENDING_GRIDS = new ConcurrentHashMap<>();

    private RecipeTreeUploadContextBridge() {
    }

    public static void rememberGrid(ServerPlayer player, @Nullable IGrid grid) {
        if (player == null) {
            return;
        }
        if (grid == null) {
            PENDING_GRIDS.remove(player.getUUID());
            return;
        }
        PENDING_GRIDS.put(player.getUUID(), grid);
    }

    public static @Nullable IGrid getRememberedGrid(ServerPlayer player) {
        return player == null ? null : PENDING_GRIDS.get(player.getUUID());
    }

    public static void clear(ServerPlayer player) {
        if (player != null) {
            PENDING_GRIDS.remove(player.getUUID());
        }
    }
}
