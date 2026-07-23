package com.lhy.ae2utility.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import appeng.helpers.WirelessCraftingTerminalMenuHost;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.locator.MenuLocators;

import com.lhy.ae2utility.compat.WcwtCompat;

/**
 * 在玩家背包中查找无线终端并解析为 {@link WirelessTerminalMenuHost}（已联网或 WCWT 量子链路可用）。
 *
 * <p>1.20.1 forge：定位 API 与 1.21 一致（{@link MenuLocators#forInventorySlot}）。
 * Curios 为可选依赖；若安装 Curios 且玩家身上有 Curios 物品栏能力，会通过 Forge capability 探测；
 * 此处精简版仅扫描玩家背包主物品栏，不依赖 Curios 类（避免硬依赖）。</p>
 */
public final class WirelessTerminalContextResolver {
    private static final long CACHE_TTL_MS = 150L;
    private static final Map<UUID, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private WirelessTerminalContextResolver() {
    }

    public static Resolution resolve(Player player) {
        Resolution cached = getCachedResolution(player);
        if (cached != null) {
            return cached;
        }

        boolean foundWirelessTerminal = false;

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (!WirelessEncodeTerminalItems.mayProvideWirelessEncoding(stack)) {
                continue;
            }

            foundWirelessTerminal = true;
            int lockedInventorySlot = slot < player.getInventory().items.size() ? slot : -1;
            String sourceDescription = lockedInventorySlot >= 0 ? "inventory slot " + slot : "player slot " + slot;
            WirelessTerminalMenuHost host = locateFromInventorySlot(player, slot);
            if (host == null || host.getItemStack().isEmpty()) {
                continue;
            }

            refreshWcwtHostBeforeLinkRead(host);
            if (hostConnectedOrWcwtGridUsable(host)) {
                Resolution resolution = new Resolution(Status.READY, host, lockedInventorySlot, sourceDescription);
                cacheResolution(player, resolution);
                return resolution;
            }
        }

        for (CuriosWirelessTerminalLookup.Candidate candidate : CuriosWirelessTerminalLookup.findCandidates(player)) {
            foundWirelessTerminal = true;
            WirelessTerminalMenuHost host = CuriosWirelessTerminalLookup.locate(player, candidate);
            if (host == null || host.getItemStack().isEmpty()) {
                continue;
            }

            refreshWcwtHostBeforeLinkRead(host);
            if (hostConnectedOrWcwtGridUsable(host)) {
                Resolution resolution = new Resolution(
                        Status.READY,
                        host,
                        -1,
                        "curios " + candidate.slotId() + "[" + candidate.slotIndex() + "]");
                cacheResolution(player, resolution);
                return resolution;
            }
        }

        Resolution resolution = new Resolution(foundWirelessTerminal ? Status.DISCONNECTED : Status.NO_WIRELESS, null, -1, "none");
        cacheResolution(player, resolution);
        return resolution;
    }

    private static @Nullable WirelessTerminalMenuHost locateFromInventorySlot(Player player, int slot) {
        WirelessTerminalMenuHost host = MenuLocators.forInventorySlot(slot).locate(player, WirelessTerminalMenuHost.class);
        if (host != null) {
            return host;
        }
        WirelessCraftingTerminalMenuHost craftingHost =
                MenuLocators.forInventorySlot(slot).locate(player, WirelessCraftingTerminalMenuHost.class);
        if (craftingHost != null) {
            return craftingHost;
        }
        return null;
    }

    private static void refreshWcwtHostBeforeLinkRead(WirelessTerminalMenuHost host) {
        if (!WcwtCompat.isWcwtHost(host)) {
            return;
        }
        try {
            // 触发节点刷新
            host.getActionableNode();
            var m = host.getClass().getDeclaredMethod("updateLinkStatus");
            m.setAccessible(true);
            m.invoke(host);
        } catch (Throwable ignored) {
        }
    }

    private static boolean hostConnectedOrWcwtGridUsable(WirelessTerminalMenuHost host) {
        // 1.20.1：rangeCheck() 返回 true 表示无线终端在范围内且已连接
        if (host.rangeCheck()) {
            return true;
        }
        if (!WcwtCompat.isWcwtHost(host)) {
            return false;
        }
        // WCWT 量子链路：即使 rangeCheck 失败，节点可能仍可用
        var node = host.getActionableNode();
        return node != null && node.getGrid() != null;
    }

    public record Resolution(Status status, @Nullable WirelessTerminalMenuHost host, int inventorySlot, String sourceDescription) {
        public boolean isReady() {
            return status == Status.READY && host != null;
        }
    }

    public enum Status {
        READY,
        NO_WIRELESS,
        DISCONNECTED
    }

    private static @Nullable Resolution getCachedResolution(Player player) {
        CacheEntry entry = CACHE.get(player.getUUID());
        if (entry == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        if (now > entry.expiresAtMs || entry.menu != player.containerMenu) {
            CACHE.remove(player.getUUID(), entry);
            return null;
        }

        return entry.resolution;
    }

    private static void cacheResolution(Player player, Resolution resolution) {
        CACHE.put(player.getUUID(), new CacheEntry(player.containerMenu, resolution, System.currentTimeMillis() + CACHE_TTL_MS));
    }

    private record CacheEntry(AbstractContainerMenu menu, Resolution resolution, long expiresAtMs) {
    }
}
