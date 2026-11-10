package com.lhy.ae2utility.service;

import java.util.ArrayList;
import java.util.List;

import com.lhy.ae2utility.debug.InventoryPatternUploadDebug;
import com.lhy.ae2utility.network.UploadInventoryPatternsToMatrixPacket;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class InventoryPatternMatrixUploadService {
    private InventoryPatternMatrixUploadService() {
    }

    public static void handle(ServerPlayer player, UploadInventoryPatternsToMatrixPacket payload) {
        if (player == null || payload == null || payload.slotIndices().isEmpty()) {
            InventoryPatternUploadDebug.warn("matrix_upload", "playerOrPayload invalid player={} payloadEmpty={}",
                    player != null, payload == null || payload.slotIndices().isEmpty());
            return;
        }

        IGrid grid = resolveGrid(player);
        if (grid == null) {
            InventoryPatternUploadDebug.warn("matrix_upload", "no grid resolved slots={}", payload.slotIndices());
            return;
        }
        InventoryPatternUploadDebug.info("matrix_upload", "resolved grid={} nodeClasses={}",
                grid.getClass().getName(), summarizeMachineClasses(grid));

        List<Integer> remainingSlots = new ArrayList<>();

        for (Integer slotIndex : payload.slotIndices()) {
            if (slotIndex == null || slotIndex.intValue() < 0 || slotIndex.intValue() >= player.getInventory().getContainerSize()) {
                continue;
            }
            ItemStack stack = player.getInventory().getItem(slotIndex.intValue());
            if (stack.isEmpty()) {
                continue;
            }

            InventoryPatternUploadDebug.info("matrix_upload", "slot={} count={} item={}",
                    slotIndex, stack.getCount(), stack.getItem());

            boolean fullyUploaded = true;

            // Only try matrix for matrix-capable patterns
            if (isMatrixCapablePattern(player, stack)) {
                while (!stack.isEmpty()) {
                    if (!uploadSinglePatternToMatrix(player, stack.copyWithCount(1), grid)) {
                        InventoryPatternUploadDebug.info("matrix_upload", "slot={} stopped by upload failure", slotIndex);
                        fullyUploaded = false;
                        break;
                    }
                    stack.shrink(1);
                    InventoryPatternUploadDebug.info("matrix_upload", "slot={} uploaded one remaining={}", slotIndex, stack.getCount());
                }
            } else {
                fullyUploaded = false;
            }

            if (!fullyUploaded && !stack.isEmpty()) {
                remainingSlots.add(slotIndex);
            }

            player.getInventory().setItem(slotIndex.intValue(), stack.isEmpty() ? ItemStack.EMPTY : stack);
        }

        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();

        if (!remainingSlots.isEmpty()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.lhy.ae2utility.network.FallbackToProviderSelectionPacket(remainingSlots));
            InventoryPatternUploadDebug.info("matrix_upload", "sent fallback packet with slots={}", remainingSlots);
        } else {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("全部样板已自动上传到装配矩阵").withStyle(net.minecraft.ChatFormatting.GREEN), true);
        }
    }

    private static boolean isMatrixCapablePattern(ServerPlayer player, ItemStack stack) {
        try {
            IPatternDetails details = appeng.api.crafting.PatternDetailsHelper.decodePattern(stack, player.level());
            return details instanceof AECraftingPattern
                    || details instanceof AESmithingTablePattern
                    || details instanceof AEStonecuttingPattern;
        } catch (Throwable t) {
            InventoryPatternUploadDebug.warn("matrix_upload", "decode failed stack={} error={}", stack, t.toString());
            return false;
        }
    }

    private static boolean uploadSinglePatternToMatrix(ServerPlayer player, ItemStack pattern, IGrid grid) {
        if (!com.lhy.ae2utility.integration.eaep.EaepReflection.isLoaded()) {
            return false;
        }
        boolean uploaded = com.lhy.ae2utility.integration.eaep.EaepDirectCompat
                .uploadPatternToMatrix(player, pattern.copy(), grid);
        InventoryPatternUploadDebug.info("matrix_upload", "EAEP upload returned={} pattern={}", uploaded, pattern);
        return uploaded;
    }

    private static IGrid resolveGrid(ServerPlayer player) {
        return InventoryPatternUploadGridResolver.resolve(player);
    }

    private static List<String> summarizeMachineClasses(IGrid grid) {
        try {
            List<String> names = new ArrayList<>();
            for (Class<?> machineClass : grid.getMachineClasses()) {
                String name = machineClass == null ? "null" : machineClass.getName();
                if (name.contains("matrix") || name.contains("Pattern") || name.contains("Assembler")) {
                    names.add(name);
                }
            }
            return names;
        } catch (Throwable t) {
            return List.of("error:" + t);
        }
    }
}
