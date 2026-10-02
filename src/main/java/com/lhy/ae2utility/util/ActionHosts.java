package com.lhy.ae2utility.util;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;

/** Null-safe access to an {@link IActionHost}'s grid. */
public final class ActionHosts {
    private ActionHosts() {
    }

    public static @Nullable IGrid gridOf(@Nullable IActionHost host) {
        if (host == null) {
            return null;
        }
        IGridNode node = host.getActionableNode();
        return node != null ? node.getGrid() : null;
    }
}
