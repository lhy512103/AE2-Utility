package com.lhy.ae2utility.service;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.core.definitions.AEItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

import com.lhy.ae2utility.network.ClearPatternsPacket;

public final class ClearPatternsService {
    private ClearPatternsService() {
    }

    public static void handle(ServerPlayer player, ClearPatternsPacket msg) {
        Inventory inventory = player.getInventory();
        int clearedCount = 0;

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack)) {
                int count = stack.getCount();
                inventory.setItem(slot, AEItems.BLANK_PATTERN.stack(count));
                clearedCount += count;
            }
        }

        if (clearedCount > 0) {
            mergeBlankPatterns(player, inventory);
            player.containerMenu.broadcastChanges();
        }
    }

    private static void mergeBlankPatterns(ServerPlayer player, Inventory inventory) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(AEItems.BLANK_PATTERN.asItem())) {
                total += stack.getCount();
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }

        if (total <= 0) {
            return;
        }

        int maxStackSize = AEItems.BLANK_PATTERN.stack().getMaxStackSize();
        for (int slot = 0; slot < inventory.getContainerSize() && total > 0; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                int amount = Math.min(total, maxStackSize);
                inventory.setItem(slot, AEItems.BLANK_PATTERN.stack(amount));
                total -= amount;
            }
        }

        if (total > 0) {
            ItemHandlerHelper.giveItemToPlayer(player, AEItems.BLANK_PATTERN.stack(total));
        }
    }
}
