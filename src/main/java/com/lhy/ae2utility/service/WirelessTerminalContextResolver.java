package com.lhy.ae2utility.service;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;

import appeng.helpers.WirelessTerminalMenuHost;
import appeng.integration.modules.curios.CuriosIntegration;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.MenuLocators;

import com.lhy.ae2utility.Ae2UtilityMod;

public final class WirelessTerminalContextResolver {
    private WirelessTerminalContextResolver() {
    }

    public static Resolution resolve(Player player) {
        boolean foundWirelessTerminal = false;

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof WirelessTerminalItem)) {
                continue;
            }

            foundWirelessTerminal = true;
            int lockedInventorySlot = slot < player.getInventory().items.size() ? slot : -1;
            String sourceDescription = lockedInventorySlot >= 0 ? "inventory slot " + slot : "player slot " + slot;
            var locator = MenuLocators.forInventorySlot(slot);
            var host = locator.locate(player, WirelessTerminalMenuHost.class);
            if (host == null || !host.isValid()) {
                continue;
            }

            if (host.getLinkStatus().connected()) {
                return new Resolution(Status.READY, host, lockedInventorySlot, sourceDescription);
            }
        }

        var curiosItems = player.getCapability(CuriosIntegration.ITEM_HANDLER);
        if (curiosItems != null) {
            for (int slot = 0; slot < curiosItems.getSlots(); slot++) {
                var stack = curiosItems.getStackInSlot(slot);
                if (stack.isEmpty() || !(stack.getItem() instanceof WirelessTerminalItem)) {
                    continue;
                }

                foundWirelessTerminal = true;
                String sourceDescription = "curios slot " + slot;
                var locator = MenuLocators.forCurioSlot(slot);
                var host = locator.locate(player, WirelessTerminalMenuHost.class);
                if (host == null || !host.isValid()) {
                    continue;
                }

                if (host.getLinkStatus().connected()) {
                    return new Resolution(Status.READY, host, -1, sourceDescription);
                }
            }
        }

        return new Resolution(foundWirelessTerminal ? Status.DISCONNECTED : Status.NO_WIRELESS, null, -1, "none");
    }

    public record Resolution(Status status, @Nullable WirelessTerminalMenuHost<?> host, int inventorySlot, String sourceDescription) {
        public boolean isReady() {
            return status == Status.READY && host != null;
        }
    }

    public enum Status {
        READY,
        NO_WIRELESS,
        DISCONNECTED
    }
}
