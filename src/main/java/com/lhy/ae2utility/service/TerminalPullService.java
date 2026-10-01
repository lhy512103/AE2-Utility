package com.lhy.ae2utility.service;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.items.storage.ViewCellItem;
import appeng.menu.me.common.MEStorageMenu;

import com.lhy.ae2utility.network.PullRecipeInputsPacket;
import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;
import com.lhy.ae2utility.util.MeIngredientExtraction;

/** Server-side item extraction for the JEI terminal transfer button. */
public final class TerminalPullService {
    private TerminalPullService() {
    }

    public static void handle(Player player, PullRecipeInputsPacket payload) {
        if (!(player instanceof ServerPlayer server) || !(server.containerMenu instanceof MEStorageMenu menu)) {
            return;
        }
        ITerminalHost host = menu.getHost();
        MEStorage storage = host.getInventory();
        if (storage == null || !menu.isPowered()) {
            server.sendSystemMessage(Component.translatable("message.ae2utility.no_terminal_link"));
            return;
        }

        IEnergySource energy = resolveEnergySource(menu);
        IActionSource source = menu.getActionSource();
        var filter = ViewCellItem.createItemFilter(menu.getViewCells());
        List<ItemStack> missing = new ArrayList<>();
        for (RequestedIngredient request : payload.requestedIngredients()) {
            if (request == null || request.count() <= 0 || request.alternatives().isEmpty()) {
                continue;
            }
            List<ItemStack> extracted = MeIngredientExtraction.extractAlternatives(
                    storage, energy, source, filter, request.alternatives(), request.count());
            for (ItemStack stack : extracted) {
                ItemStack remainder = insertPlayer(server, stack);
                if (!remainder.isEmpty()) {
                    var key = appeng.api.stacks.AEItemKey.of(remainder);
                    if (key != null) {
                        StorageHelper.poweredInsert(energy, storage, key, remainder.getCount(), source);
                    }
                }
            }
            int extractedCount = extracted.stream().mapToInt(ItemStack::getCount).sum();
            if (extractedCount < request.count()) {
                missing.add(request.alternatives().get(0).copyWithCount(request.count() - extractedCount));
            }
        }
        if (!missing.isEmpty()) {
            server.sendSystemMessage(Component.translatable("message.ae2utility.missing_ingredients"));
        }
    }

    private static ItemStack insertPlayer(ServerPlayer player, ItemStack stack) {
        int remaining = stack.getCount();
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack destination = player.getInventory().getItem(slot);
            if (destination.isEmpty()) {
                player.getInventory().setItem(slot, stack.copyWithCount(remaining));
                return ItemStack.EMPTY;
            }
            if (ItemStack.isSameItemSameTags(destination, stack)
                    && destination.getCount() < destination.getMaxStackSize()) {
                int moved = Math.min(remaining, destination.getMaxStackSize() - destination.getCount());
                destination.grow(moved);
                remaining -= moved;
            }
        }
        return stack.copyWithCount(remaining);
    }

    private static IEnergySource resolveEnergySource(MEStorageMenu menu) {
        var node = menu.getNetworkNode();
        return node == null || node.getGrid() == null ? null : node.getGrid().getEnergyService();
    }
}
