package com.lhy.ae2utility.jei;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.world.item.crafting.Ingredient;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEKey;
import appeng.menu.me.common.MEStorageMenu;

/**
 * 与 AE2 终端 JEI 拉配方预览同源：依据 {@link MEStorageMenu#getClientRepo()} 快照中
 * {@code entry.isCraftable()} 的输出键判断是否可 autocraft。
 */
public final class ClientRepoCraftableIndex {
    private static long tickGeneration;

    private static long cacheGeneration = Long.MIN_VALUE;
    private static @Nullable MEStorageMenu cacheMenu;
    private static @Nullable Set<AEKey> cacheCraftableKeys;

    private ClientRepoCraftableIndex() {}

    public static void advanceClientTick() {
        tickGeneration++;
    }

    public static void invalidate() {
        cacheGeneration = Long.MIN_VALUE;
        cacheMenu = null;
        cacheCraftableKeys = null;
    }

    public static boolean isCraftableOutput(MEStorageMenu menu, AEKey key) {
        if (key == null || menu.getClientRepo() == null) {
            return false;
        }
        Set<AEKey> keys = craftableKeysForMenu(menu);
        return keys.contains(key);
    }

    public static boolean hasCraftableItemMatchingIngredient(MEStorageMenu menu, Ingredient ingredient) {
        var repo = menu.getClientRepo();
        if (repo == null || ingredient.isEmpty()) {
            return false;
        }
        for (var entry : repo.getByIngredient(ingredient)) {
            if (entry.isCraftable()) {
                return true;
            }
        }
        return false;
    }

    private static Set<AEKey> craftableKeysForMenu(MEStorageMenu menu) {
        if (cacheCraftableKeys != null && cacheMenu == menu && cacheGeneration == tickGeneration) {
            return cacheCraftableKeys;
        }
        rebuildForMenu(menu);
        return cacheCraftableKeys;
    }

    private static void rebuildForMenu(MEStorageMenu menu) {
        var repo = menu.getClientRepo();
        cacheMenu = menu;
        cacheGeneration = tickGeneration;
        if (repo == null) {
            cacheCraftableKeys = Set.of();
            return;
        }
        Set<AEKey> keys = new HashSet<>();
        for (var entry : repo.getAllEntries()) {
            if (entry.isCraftable()) {
                AEKey what = entry.getWhat();
                if (what != null) {
                    keys.add(what);
                }
            }
        }
        cacheCraftableKeys = keys;
    }
}
