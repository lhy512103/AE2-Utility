package com.lhy.ae2utility.service;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
import appeng.menu.AEBaseMenu;

import com.lhy.ae2utility.compat.WcwtCompat;
import com.lhy.ae2utility.util.ActionHosts;
import com.lhy.ae2utility.network.BatchUploadStepPacket;
import com.lhy.ae2utility.network.InventoryUploadRemainingPacket;
import com.lhy.ae2utility.network.ModNetworking;
import com.lhy.ae2utility.network.OpenEaepProviderSelectionPacket;
import com.lhy.ae2utility.network.UploadInventoryPatternStepPacket;
import com.lhy.ae2utility.network.UploadInventoryPatternsPacket;

/**
 * 样板编码终端 Alt+上传：背包中的合成/锻造/切石样板先写入装配矩阵，其余逐个进入 EAEP 待定上传并选择供应器。
 */
public final class InventoryPatternUploadService {
    public static final String SEARCH_KEY_TAG = "ae2utility_provider_search_key";

    private InventoryPatternUploadService() {
    }

    public static void uploadToMatrix(ServerPlayer player, UploadInventoryPatternsPacket msg) {
        if (!ModList.get().isLoaded("extendedae_plus")) {
            return;
        }
        IGrid grid = resolveGrid(player);
        List<Integer> remaining = new ArrayList<>();
        var inventory = player.getInventory();
        for (int slot : msg.slots()) {
            if (slot < 0 || slot >= inventory.getContainerSize()) {
                continue;
            }
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
                continue;
            }
            if (grid != null && isMatrixCapable(player, stack)) {
                while (!stack.isEmpty() && com.extendedae_plus.util.uploadPattern.MatrixUploadUtil
                        .uploadPatternToMatrix(player, uploadCopy(stack), grid)) {
                    stack.shrink(1);
                }
                inventory.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
            }
            if (!stack.isEmpty()) {
                remaining.add(slot);
            }
        }
        inventory.setChanged();
        if (remaining.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.ae2utility.inventory_upload_matrix_done")
                    .withStyle(ChatFormatting.GREEN), true);
        } else {
            ModNetworking.sendToPlayer(player, new InventoryUploadRemainingPacket(remaining));
        }
    }

    public static void uploadStep(ServerPlayer player, UploadInventoryPatternStepPacket msg) {
        boolean awaitingProvider = false;
        try {
            awaitingProvider = beginProviderUpload(player, msg.slot());
        } finally {
            ModNetworking.sendToPlayer(player, new BatchUploadStepPacket(awaitingProvider));
        }
    }

    private static boolean beginProviderUpload(ServerPlayer player, int slot) {
        var inventory = player.getInventory();
        if (!ModList.get().isLoaded("extendedae_plus") || slot < 0 || slot >= inventory.getContainerSize()) {
            return false;
        }
        ItemStack stack = inventory.getItem(slot);
        if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
            return false;
        }
        var tag = stack.getTag();
        String searchKey = tag != null ? tag.getString(SEARCH_KEY_TAG) : "";
        boolean crafting = searchKey.isBlank() && isMatrixCapable(player, stack);
        if (com.extendedae_plus.util.uploadPattern.ProviderUploadUtil.beginPendingCtrlQUpload(player,
                uploadCopy(stack)) == null) {
            return false;
        }
        stack.shrink(1);
        inventory.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        inventory.setChanged();
        ModNetworking.sendToPlayer(player, new OpenEaepProviderSelectionPacket(crafting, searchKey));
        return true;
    }

    private static ItemStack uploadCopy(ItemStack stack) {
        ItemStack copy = stack.copyWithCount(1);
        var tag = copy.getTag();
        if (tag != null) {
            tag.remove(SEARCH_KEY_TAG);
            if (tag.isEmpty()) {
                copy.setTag(null);
            }
        }
        return copy;
    }

    private static boolean isMatrixCapable(ServerPlayer player, ItemStack stack) {
        IPatternDetails details = PatternDetailsHelper.decodePattern(stack, player.level());
        return details instanceof AECraftingPattern || details instanceof AESmithingTablePattern
                || details instanceof AEStonecuttingPattern;
    }

    private static @Nullable IGrid resolveGrid(ServerPlayer player) {
        Object host = null;
        if (player.containerMenu instanceof AEBaseMenu menu) {
            host = menu.getTarget();
        }
        if (!(host instanceof IActionHost) && WcwtCompat.isWcwtMenu(player.containerMenu)) {
            host = WcwtCompat.extractTerminalHost(player.containerMenu);
        }
        if (host instanceof IActionHost actionHost) {
            IGrid grid = ActionHosts.gridOf(actionHost);
            if (grid != null) {
                return grid;
            }
        }
        var resolution = WirelessTerminalContextResolver.resolve(player);
        var wirelessHost = resolution.host();
        if (resolution.isReady() && wirelessHost != null) {
            return ActionHosts.gridOf(wirelessHost);
        }
        return null;
    }
}
