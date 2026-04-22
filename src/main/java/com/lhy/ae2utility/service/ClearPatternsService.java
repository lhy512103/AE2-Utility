package com.lhy.ae2utility.service;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.core.definitions.AEItems;
import com.lhy.ae2utility.network.ClearPatternsPacket;

public class ClearPatternsService {

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
                // 如果有清理操作，则向客户端广播背包更新 (Broadcast inventory changes if any patterns were cleared)
                if (clearedCount > 0) {
                    player.containerMenu.broadcastChanges();
                }
            }
        });
    }
}
