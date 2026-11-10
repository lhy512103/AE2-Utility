package com.lhy.ae2utility.jei;

import java.util.function.BooleanSupplier;

import net.minecraft.client.player.LocalPlayer;

/**
 * 客户端 JEI 热路径的逐 tick 轻量缓存上下文。
 */
public final class JeiClientCacheContext {
    private static long tickGeneration;
    private static long playerMayEncodeTick = Long.MIN_VALUE;
    private static int playerMayEncodePlayerId = Integer.MIN_VALUE;
    private static boolean playerMayEncodeCached;

    private JeiClientCacheContext() {
    }

    public static void advanceClientTick() {
        tickGeneration++;
        playerMayEncodeTick = Long.MIN_VALUE;
        playerMayEncodePlayerId = Integer.MIN_VALUE;
    }

    public static long getTickGeneration() {
        return tickGeneration;
    }

    public static boolean getPlayerMayEncodePatterns(LocalPlayer player, BooleanSupplier resolver) {
        if (player == null) {
            return false;
        }

        int playerId = player.getId();
        if (playerMayEncodeTick == tickGeneration && playerMayEncodePlayerId == playerId) {
            return playerMayEncodeCached;
        }

        playerMayEncodeCached = resolver.getAsBoolean();
        playerMayEncodeTick = tickGeneration;
        playerMayEncodePlayerId = playerId;
        return playerMayEncodeCached;
    }
}
