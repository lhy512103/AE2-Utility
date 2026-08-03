package com.lhy.ae2utility.service;

import org.jetbrains.annotations.Nullable;

import com.lhy.ae2utility.compat.WcwtCompat;
import com.lhy.ae2utility.integration.eaep.EaepReflection;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;
import net.minecraft.server.level.ServerPlayer;

/** Resolves the AE network that owns the player's currently active pattern terminal session. */
public final class InventoryPatternUploadGridResolver {
    private InventoryPatternUploadGridResolver() {
    }

    public static @Nullable IGrid resolve(ServerPlayer player) {
        if (player == null) {
            return null;
        }

        IGrid menuGrid = resolveFromOpenMenu(player);
        return menuGrid != null ? menuGrid : EaepReflection.findPlayerGrid(player);
    }

    private static @Nullable IGrid resolveFromOpenMenu(ServerPlayer player) {
        if (player.containerMenu instanceof PatternEncodingTermMenu menu) {
            return resolveFromActionHost(((AEBaseMenu) menu).getTarget());
        }
        if (WcwtCompat.isWcwtMenu(player.containerMenu)) {
            return resolveFromActionHost(WcwtCompat.extractTerminalHost(player.containerMenu));
        }
        return null;
    }

    private static @Nullable IGrid resolveFromActionHost(Object candidate) {
        try {
            if (candidate instanceof IActionHost host && host.getActionableNode() != null) {
                return host.getActionableNode().getGrid();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}