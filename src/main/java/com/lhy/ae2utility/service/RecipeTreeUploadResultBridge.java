package com.lhy.ae2utility.service;

import com.lhy.ae2utility.network.RecipeTreeUploadResultPacket;

import net.minecraft.server.level.ServerPlayer;

public final class RecipeTreeUploadResultBridge {
    private static final String PENDING_NAME_KEY = "ae2utility_recipe_tree_pending_name";

    private RecipeTreeUploadResultBridge() {
    }

    public static void rememberPendingName(ServerPlayer player, String patternName) {
        if (player == null) {
            return;
        }
        if (patternName == null || patternName.isBlank()) {
            clearPendingName(player);
            return;
        }
        player.getPersistentData().putString(PENDING_NAME_KEY, patternName);
    }

    public static void clearPendingName(ServerPlayer player) {
        if (player != null) {
            player.getPersistentData().remove(PENDING_NAME_KEY);
            RecipeTreeUploadContextBridge.clear(player);
        }
    }

    public static void sendImmediateResult(ServerPlayer player, String patternName, boolean uploaded) {
        if (player == null || patternName == null || patternName.isBlank()) {
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new RecipeTreeUploadResultPacket(patternName, uploaded));
    }

    public static void flushPendingResult(ServerPlayer player, boolean uploaded) {
        if (player == null) {
            return;
        }
        String patternName = player.getPersistentData().getString(PENDING_NAME_KEY);
        if (patternName == null || patternName.isBlank()) {
            return;
        }
        clearPendingName(player);
        sendImmediateResult(player, patternName, uploaded);
    }
}
