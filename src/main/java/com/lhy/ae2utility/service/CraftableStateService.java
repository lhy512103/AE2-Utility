package com.lhy.ae2utility.service;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.ITerminalHost;
import appeng.menu.me.common.MEStorageMenu;

import com.lhy.ae2utility.network.CraftableStatePacket;
import com.lhy.ae2utility.network.QueryCraftableStatePacket;

public final class CraftableStateService {
    private CraftableStateService() {}

    public static void handle(Player player, QueryCraftableStatePacket payload) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        IGrid grid = null;

        // 1. Try to resolve from open menu
        if (serverPlayer.containerMenu instanceof MEStorageMenu storageMenu) {
            ITerminalHost host = storageMenu.getHost();
            if (host instanceof IActionHost ah && ah.getActionableNode() != null) {
                grid = ah.getActionableNode().getGrid();
            }
        }

        // 2. Try wireless terminal
        if (grid == null) {
            var resolution = WirelessTerminalContextResolver.resolve(serverPlayer);
            if (resolution.status() == WirelessTerminalContextResolver.Status.READY && resolution.host() != null) {
                WirelessTerminalMenuHost<?> host = resolution.host();
                if (host.getActionableNode() != null) {
                    grid = host.getActionableNode().getGrid();
                }
            }
        }

        List<AEKey> craftableKeys = new ArrayList<>();
        List<AEKey> uncraftableKeys = new ArrayList<>();

        if (grid != null) {
            var craftingService = grid.getCraftingService();
            for (AEKey key : payload.keys()) {
                if (craftingService.isCraftable(key)) {
                    craftableKeys.add(key);
                } else {
                    uncraftableKeys.add(key);
                }
            }
        } else {
            // No grid available, all are uncraftable
            uncraftableKeys.addAll(payload.keys());
        }

        PacketDistributor.sendToPlayer(serverPlayer, new CraftableStatePacket(craftableKeys, uncraftableKeys));
    }
}
