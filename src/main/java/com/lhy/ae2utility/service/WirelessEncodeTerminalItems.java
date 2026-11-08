package com.lhy.ae2utility.service;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import appeng.items.tools.powered.WirelessTerminalItem;

/**
 * 与 {@link com.lhy.ae2utility.service.WirelessTerminalContextResolver} / JEI 编码按钮一致：
 * 背包/Curios 中可作为「无线编码候选」的物品（实际是否启用还要看主机 {@link appeng.helpers.WirelessTerminalMenuHost#getLinkStatus()} 是否联网）。
 */
public final class WirelessEncodeTerminalItems {
    /** ME 综合无线工作终端（WCWT）；{@link WirelessTerminalItem} 已覆盖其子类，命名空间用于额外兜底与其它分支物品。 */
    public static final String WCWT_NAMESPACE = "wireless_comprehensive_work_terminal";

    private WirelessEncodeTerminalItems() {
    }

    public static boolean mayProvideWirelessEncoding(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof WirelessTerminalItem) {
            return true;
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (WCWT_NAMESPACE.equals(key.getNamespace())) {
            return true;
        }
        String id = key.toString();
        return id.contains("pattern_encoding") || id.contains("universal_terminal");
    }
}
