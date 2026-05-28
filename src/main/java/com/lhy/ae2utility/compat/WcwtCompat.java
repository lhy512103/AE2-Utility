package com.lhy.ae2utility.compat;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import appeng.api.storage.ITerminalHost;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;

public final class WcwtCompat {
    private static final String WCWT_MENU_CLASS = "com.lhy.wcwt.menu.WirelessComprehensiveWorkTerminalMenu";
    private static final String WCWT_HOST_CLASS = "com.lhy.wcwt.helpers.WirelessComprehensiveWorkTerminalMenuHost";

    private WcwtCompat() {
    }

    public static boolean isPatternEncodingLikeMenu(@Nullable AbstractContainerMenu menu) {
        if (menu instanceof PatternEncodingTermMenu) {
            return true;
        }
        if (menu instanceof MEStorageMenu me && me.getHost() instanceof IPatternTerminalMenuHost) {
            return true;
        }
        return menu != null && menu.getClass().getName().equals(WCWT_MENU_CLASS);
    }

    public static boolean isWcwtMenu(@Nullable AbstractContainerMenu menu) {
        return menu != null && menu.getClass().getName().equals(WCWT_MENU_CLASS);
    }

    public static boolean isOpenPatternEncodingLikeMenu(@Nullable LocalPlayer player) {
        return player != null && isPatternEncodingLikeMenu(player.containerMenu);
    }

    public static @Nullable ITerminalHost extractTerminalHost(@Nullable AbstractContainerMenu menu) {
        if (menu instanceof MEStorageMenu storageMenu) {
            return storageMenu.getHost();
        }
        if (!isWcwtMenu(menu)) {
            return null;
        }
        try {
            var method = menu.getClass().getMethod("getMenuHost");
            Object host = method.invoke(menu);
            return host instanceof ITerminalHost terminalHost ? terminalHost : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static @Nullable IGrid resolveGrid(@Nullable AbstractContainerMenu menu) {
        ITerminalHost host = extractTerminalHost(menu);
        if (host instanceof IActionHost actionHost && actionHost.getActionableNode() != null) {
            return actionHost.getActionableNode().getGrid();
        }
        return null;
    }

    public static boolean isWcwtHost(@Nullable Object host) {
        return host != null && host.getClass().getName().equals(WCWT_HOST_CLASS);
    }

    public static boolean isPlayerUsingWcwt(@Nullable Player player) {
        return player != null && isWcwtMenu(player.containerMenu);
    }
}
