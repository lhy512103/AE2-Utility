package com.lhy.ae2utility.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/** Shared client-thread tick context; identity-scoped, never keyed by recycled entity IDs. */
public final class JeiClientCacheContext {
    private static long tickGeneration;
    private static long playerMayEncodeTick = Long.MIN_VALUE;
    private static LocalPlayer playerMayEncodePlayer;
    private static boolean playerMayEncodeCached;
    private static long inventoryTick = Long.MIN_VALUE;
    private static long inventoryVersion;
    private static final List<ItemStack> inventorySnapshot = new ArrayList<>();

    private JeiClientCacheContext() {}

    public static void advanceClientTick() { tickGeneration++; }
    public static long getTickGeneration() { return tickGeneration; }

    public static void clear() {
        tickGeneration++;
        playerMayEncodeTick = Long.MIN_VALUE;
        playerMayEncodePlayer = null;
        playerMayEncodeCached = false;
        inventoryTick = Long.MIN_VALUE;
        inventorySnapshot.clear();
        inventoryVersion++;
    }

    /** Small player inventory only, sampled at most once per tick for all layouts.
     * Unlike a tick number, this changes only with actual stack identity/NBT/count changes.
     * No ME/network storage enumeration occurs here.
     */
    public static long inventoryVersion() {
        if (inventoryTick == tickGeneration) return inventoryVersion;
        inventoryTick = tickGeneration;
        var player = Minecraft.getInstance().player;
        int size = player == null ? 0 : player.getInventory().getContainerSize();
        boolean changed = inventorySnapshot.size() != size;
        if (changed) {
            inventorySnapshot.clear();
            for (int i = 0; i < size; i++) inventorySnapshot.add(player.getInventory().getItem(i).copy());
        } else {
            for (int i = 0; i < size; i++) {
                ItemStack current = player.getInventory().getItem(i);
                ItemStack previous = inventorySnapshot.get(i);
                if (current.getCount() != previous.getCount() || !ItemStack.isSameItemSameTags(current, previous)) {
                    changed = true;
                    inventorySnapshot.set(i, current.copy());
                }
            }
        }
        if (changed) inventoryVersion++;
        return inventoryVersion;
    }

    public static boolean getPlayerMayEncodePatterns(LocalPlayer player, BooleanSupplier resolver) {
        if (player == null) return false;
        if (playerMayEncodeTick == tickGeneration && playerMayEncodePlayer == player) return playerMayEncodeCached;
        playerMayEncodeCached = resolver.getAsBoolean();
        playerMayEncodeTick = tickGeneration;
        playerMayEncodePlayer = player;
        return playerMayEncodeCached;
    }
}
