package com.lhy.ae2utility.service;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.core.definitions.AEItems;
import com.lhy.ae2utility.network.ClearPatternsPacket;

public class ClearPatternsService {

    /**
     * Consolidates all blank patterns in the player inventory into as few stacks as possible.
     */
    private static void mergeBlankPatternsInInventory(ServerPlayer player, Inventory inv) {
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && AEItems.BLANK_PATTERN.is(stack)) {
                total += stack.getCount();
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
        if (total <= 0) {
            return;
        }
        int maxStack = AEItems.BLANK_PATTERN.stack().getMaxStackSize();
        for (int i = 0; i < inv.getContainerSize() && total > 0; i++) {
            if (inv.getItem(i).isEmpty()) {
                int put = Math.min(total, maxStack);
                inv.setItem(i, AEItems.BLANK_PATTERN.stack(put));
                total -= put;
            }
        }
        if (total > 0) {
            ItemHandlerHelper.giveItemToPlayer(player, AEItems.BLANK_PATTERN.stack(total));
        }
    }

    public static void handle(final ClearPatternsPacket msg, final IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer player) {
                Inventory inv = player.getInventory();
                int clearedCount = 0;
                // 遍历玩家背包，将所有已编码的样板替换为空白样板 (Iterate over inventory to clear encoded patterns)
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack stack = inv.getItem(i);
                    if (!stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack)) {
                        int count = stack.getCount();
                        inv.setItem(i, new ItemStack(AEItems.BLANK_PATTERN, count));
                        clearedCount += count;
                    }
                }
                if (clearedCount > 0) {
                    mergeBlankPatternsInInventory(player, inv);
                    player.containerMenu.broadcastChanges();
                }
            }
        });
    }
}
