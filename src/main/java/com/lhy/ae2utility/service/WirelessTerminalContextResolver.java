package com.lhy.ae2utility.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import appeng.api.networking.security.IActionHost;
import appeng.helpers.WirelessCraftingTerminalMenuHost;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.integration.modules.curios.CuriosIntegration;
import appeng.menu.locator.MenuLocators;

import com.lhy.ae2utility.compat.WcwtCompat;

public final class WirelessTerminalContextResolver {
    private static final long CACHE_TTL_MS = 150L;
    private static final Map<UUID, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private WirelessTerminalContextResolver() {
    }

    /**
     * 在背包与 Curios 中查找 {@link WirelessEncodeTerminalItems#mayProvideWirelessEncoding} 认可的物品，
     * 解析为无线菜单主机且已接入网络（或 WCWT：链路状态未及时刷新但网格已可用）。
     */
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
            WirelessTerminalMenuHost<?> host = locateFromInventorySlot(player, slot);
            if (host == null || !host.isValid()) {
                continue;
            }

            refreshWcwtHostBeforeLinkRead(host);
            if (hostConnectedOrWcwtGridUsable(host)) {
                Resolution resolution = new Resolution(Status.READY, host, lockedInventorySlot, sourceDescription);
                cacheResolution(player, resolution);
                return resolution;
            }
        }

        var curiosItems = player.getCapability(CuriosIntegration.ITEM_HANDLER);
        if (curiosItems != null) {
            for (int slot = 0; slot < curiosItems.getSlots(); slot++) {
                var stack = curiosItems.getStackInSlot(slot);
                if (!WirelessEncodeTerminalItems.mayProvideWirelessEncoding(stack)) {
                    continue;
                }

                foundWirelessTerminal = true;
                String sourceDescription = "curios slot " + slot;
                WirelessTerminalMenuHost<?> host = locateFromCurioSlot(player, slot);
                if (host == null || !host.isValid()) {
                    continue;
                }

                refreshWcwtHostBeforeLinkRead(host);
                if (hostConnectedOrWcwtGridUsable(host)) {
                    Resolution resolution = new Resolution(Status.READY, host, -1, sourceDescription);
                    cacheResolution(player, resolution);
                    return resolution;
                }
            }
        }

        Resolution resolution = new Resolution(foundWirelessTerminal ? Status.DISCONNECTED : Status.NO_WIRELESS, null, -1, "none");
        cacheResolution(player, resolution);
        return resolution;
    }

    /** AE2 无线终端 → {@link WirelessTerminalMenuHost}；WCWT 等综合终端 → {@link WirelessCraftingTerminalMenuHost}。 */
    private static @Nullable WirelessTerminalMenuHost<?> locateFromInventorySlot(Player player, int slot) {
        WirelessTerminalMenuHost<?> host = MenuLocators.forInventorySlot(slot).locate(player, WirelessTerminalMenuHost.class);
        if (host != null) {
            return host;
        }
        WirelessCraftingTerminalMenuHost<?> craftingHost =
                MenuLocators.forInventorySlot(slot).locate(player, WirelessCraftingTerminalMenuHost.class);
        if (craftingHost instanceof WirelessTerminalMenuHost<?> asWireless) {
            return asWireless;
        }
        return null;
    }

    private static @Nullable WirelessTerminalMenuHost<?> locateFromCurioSlot(Player player, int slot) {
        WirelessTerminalMenuHost<?> host = MenuLocators.forCurioSlot(slot).locate(player, WirelessTerminalMenuHost.class);
        if (host != null) {
            return host;
        }
        WirelessCraftingTerminalMenuHost<?> craftingHost =
                MenuLocators.forCurioSlot(slot).locate(player, WirelessCraftingTerminalMenuHost.class);
        if (craftingHost instanceof WirelessTerminalMenuHost<?> asWireless) {
            return asWireless;
        }
        return null;
    }

    /**
     * WCWT 在未打开 GUI 时 getLinkStatus 依赖 updateConnectedAccessPoint / updateLinkStatus；
     * 否则 effectiveLinkStatus 可能一直未刷新而为断开。
     */
    private static void refreshWcwtHostBeforeLinkRead(WirelessTerminalMenuHost<?> host) {
        if (!WcwtCompat.isWcwtHost(host)) {
            return;
        }
        try {
            if (host instanceof IActionHost) {
                ((IActionHost) host).getActionableNode();
            }
            var m = host.getClass().getDeclaredMethod("updateLinkStatus");
            m.setAccessible(true);
            m.invoke(host);
        } catch (Throwable ignored) {
        }
    }

    /** WCWT 量子链路等场景下：链路枚举仍非 connected，但 actionable 网格已可用，应允许 JEI 编码等服务端逻辑继续。 */
    private static boolean hostConnectedOrWcwtGridUsable(WirelessTerminalMenuHost<?> host) {
        if (host.getLinkStatus().connected()) {
            return true;
        }
        if (!WcwtCompat.isWcwtHost(host)) {
            return false;
        }
        if (!(host instanceof IActionHost ah)) {
            return false;
        }
        var node = ah.getActionableNode();
        return node != null && node.getGrid() != null;
    }

    public record Resolution(Status status, @Nullable WirelessTerminalMenuHost<?> host, int inventorySlot, String sourceDescription) {
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
