package com.lhy.ae2utility.service;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import appeng.api.stacks.AEItemKey;
import appeng.helpers.WirelessTerminalMenuHost;

import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.IActionHost;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;
import com.lhy.ae2utility.network.UniversalPullPacket;

public final class UniversalPullService {
    private UniversalPullService() {}

    public static void handle(Player player, UniversalPullPacket payload) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        var resolution = WirelessTerminalContextResolver.resolve(serverPlayer);
        if (resolution.status() != WirelessTerminalContextResolver.Status.READY || resolution.host() == null) {
            return;
        }

        WirelessTerminalMenuHost<?> terminal = resolution.host();
        var inventory = terminal.getInventory();
        if (inventory == null) {
            return;
        }

        IActionSource actionSource = resolveActionSource(serverPlayer, terminal);

        List<RequestedIngredient> requestedIngredients = payload.requestedIngredients();
        boolean maxTransfer = payload.maxTransfer();

        // Calculate missing items in player's inventory
        for (RequestedIngredient requested : requestedIngredients) {
            if (requested.count() <= 0 || requested.alternatives().isEmpty()) {
                continue;
            }

            int missingAmount = requested.count();
            // Optional: we could check player inventory to see what's already there to avoid over-pulling
            // But JEI already does this and only requests what's missing if we send it directly? 
            // No, JEI's RecipeSlotsView contains the FULL recipe. 
            // We MUST check the player inventory.
            missingAmount -= countItemsInPlayerInventory(serverPlayer, requested.alternatives());

            if (missingAmount > 0) {
                // Try to extract missing items from ME network
                for (ItemStack alternative : requested.alternatives()) {
                    if (missingAmount <= 0) break;

                    AEItemKey key = AEItemKey.of(alternative);
                    if (key == null) continue;

                    long extracted = inventory.extract(key, missingAmount, appeng.api.config.Actionable.MODULATE, actionSource);
                    if (extracted > 0) {
                        missingAmount -= (int) extracted;
                        // Give to player
                        ItemStack extractedStack = alternative.copy();
                        extractedStack.setCount((int) extracted);
                        ItemHandlerHelper.giveItemToPlayer(serverPlayer, extractedStack);
                    }
                }
            }
        }
    }

    private static IActionSource resolveActionSource(ServerPlayer player, Object host) {
        if (host instanceof IActionHost actionHost) {
            return IActionSource.ofPlayer(player, actionHost);
        }
        return IActionSource.ofPlayer(player);
    }

    private static int countItemsInPlayerInventory(Player player, List<ItemStack> alternatives) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            for (ItemStack alt : alternatives) {
                if (ItemStack.isSameItemSameComponents(stack, alt)) {
                    count += stack.getCount();
                    break;
                }
            }
        }
        return count;
    }
}
