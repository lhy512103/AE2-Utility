package com.lhy.ae2utility.client;

import org.jetbrains.annotations.Nullable;
import net.minecraft.client.player.LocalPlayer;
import appeng.menu.me.common.MEStorageMenu;
import com.lhy.ae2utility.compat.WcwtCompat;
import com.lhy.ae2utility.jei.JeiClientCacheContext;
import com.lhy.ae2utility.service.WirelessEncodeTerminalItems;

/** Viewer-independent encoding permission; reuses the existing client tick cache. */
public final class PatternEncodingAvailability {
    private PatternEncodingAvailability() {}

    public static boolean playerMayEncodePatterns(LocalPlayer player) {
        return JeiClientCacheContext.getPlayerMayEncodePatterns(player, () -> {
            if (player == null) {
                return false;
            }
            if (resolveOpenPatternEncodingLikeMenu(player) != null) {
                return true;
            }
            if (!Ae2UtilityClientConfig.allowJeiPatternEncodeWithoutOpenTerminal()) {
                return false;
            }
            return inventoryMayEncodeFromWirelessTerminal(player);
        });
    }

    private static @Nullable MEStorageMenu resolveOpenPatternEncodingLikeMenu(LocalPlayer player) {
        if (player.containerMenu instanceof MEStorageMenu me && WcwtCompat.isPatternEncodingLikeMenu(player.containerMenu)) {
            return me;
        }
        return null;
    }

    private static boolean inventoryMayEncodeFromWirelessTerminal(LocalPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (WirelessEncodeTerminalItems.mayProvideWirelessEncoding(player.getInventory().getItem(i))) {
                return true;
            }
        }
        var curios = com.lhy.ae2utility.service.CuriosWirelessTerminalLookup.items(player);
        if (curios != null) {
            for (int s = 0; s < curios.getSlots(); s++) {
                if (WirelessEncodeTerminalItems.mayProvideWirelessEncoding(curios.getStackInSlot(s))) {
                    return true;
                }
            }
        }
        return false;
    }

}
