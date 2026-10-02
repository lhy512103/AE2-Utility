package com.lhy.ae2utility.service;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.api.storage.ITerminalHost;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.me.common.MEStorageMenu;

import com.lhy.ae2utility.compat.WcwtCompat;
import com.lhy.ae2utility.util.ActionHosts;
import com.lhy.ae2utility.network.CraftableStatePacket;
import com.lhy.ae2utility.network.ModNetworking;
import com.lhy.ae2utility.network.QueryCraftableStatePacket;

public final class CraftableStateService {
    private CraftableStateService() {}

    public static void handle(Player player, QueryCraftableStatePacket payload) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        IGrid grid = null;

        if (serverPlayer.containerMenu instanceof MEStorageMenu storageMenu) {
            ITerminalHost host = WcwtCompat.extractTerminalHost(storageMenu);
            if (host instanceof IActionHost actionHost) {
                grid = ActionHosts.gridOf(actionHost);
            }
        }

        if (grid == null) {
            var resolution = WirelessTerminalContextResolver.resolve(serverPlayer);
            WirelessTerminalMenuHost host = resolution.host();
            if (resolution.isReady() && host != null) {
                grid = ActionHosts.gridOf(host);
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
            uncraftableKeys.addAll(payload.keys());
        }

        ModNetworking.sendToPlayer(serverPlayer, new CraftableStatePacket(craftableKeys, uncraftableKeys));
    }
}
