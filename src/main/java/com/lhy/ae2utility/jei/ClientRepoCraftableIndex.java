package com.lhy.ae2utility.jei;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.world.item.crafting.Ingredient;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEKey;
import appeng.menu.me.common.MEStorageMenu;

/**
 * 与 AE2 终端 JEI 拉配方预览同源：依据 {@link MEStorageMenu#getClientRepo()} 快照中 {@code entry.isCraftable()}
 * 的输出键判断是否可 autocraft。<p>
 * 在同一客户端 {@linkplain #advanceClientTick() tick} 内对同一 {@link MEStorageMenu} 实例只遍历一次 {@code getAllEntries()}，
 * 后续 {@link AEKey} 查询为集合 O(1)，避免编码箭头 / 书签 / 配方树 / 拉配方预览等在同 tick 重复全表扫描。</p>
 */
public final class ClientRepoCraftableIndex {
    /** 单调递增；每次 {@link #advanceClientTick()} 加一，用于使跨 tick 的缓存失效。 */
    private static long tickGeneration;

    private static long cacheGeneration = Long.MIN_VALUE;
    private static @Nullable MEStorageMenu cacheMenu;
    private static @Nullable Set<AEKey> cacheCraftableKeys;

    private ClientRepoCraftableIndex() {}

    /**
     * 在客户端 tick 早期调用（例如 {@link net.neoforged.neoforge.client.event.ClientTickEvent.Post} 起始处），
     * 使本 tick 内首次查询时针对当前菜单重建快照。
     */
    public static void advanceClientTick() {
        tickGeneration++;
    }

    /** 与 {@link CraftableStateCache#invalidateKeys} 等配合：样板或网络状态变更后强制下一查重建。 */
    public static void invalidate() {
        cacheGeneration = Long.MIN_VALUE;
        cacheMenu = null;
        cacheCraftableKeys = null;
    }

    /**
     * @return 若 {@code menu.getClientRepo()} 为 null 则 false；否则按终端 crafting 快照判定 {@code key} 是否为可合成输出。
     */
    public static boolean isCraftableOutput(MEStorageMenu menu, AEKey key) {
        if (key == null || menu.getClientRepo() == null) {
            return false;
        }
        Set<AEKey> keys = craftableKeysForMenu(menu);
        return keys.contains(key);
    }

    /**
     * 是否存在可合成输出与该 {@code ingredient} 匹配（JEI 物品输入槽与 AE 拉配方预览路径）。
     * <p>与 AE2 {@link MEStorageMenu#hasIngredient} 同源：经 {@code clientRepo} 的 item-id 索引
     * （{@code getByIngredient} → {@code Ingredient.getStackingIds()} → {@code getByItemId}）只命中接受的 item，
     * 再判 {@code isCraftable}；避免「全网络可合成键 × 全部候选」的 O(N×M) 扫描。</p>
     */
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
