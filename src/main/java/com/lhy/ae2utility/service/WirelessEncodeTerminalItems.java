package com.lhy.ae2utility.service;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import appeng.items.tools.powered.WirelessTerminalItem;

/**
 * 与 {@link WirelessTerminalContextResolver} / JEI 编码按钮一致：
 * 背包中可作为「无线编码候选」的物品（WCWT 综合终端命名空间兜底）。
 *
 * <p>1.20.1 forge 版本：去掉 Curios 分支（Curios 为可选依赖，无线编码主要走背包物品）。</p>
 */
public final class WirelessEncodeTerminalItems {
    public static final String WCWT_NAMESPACE = "wcwt";
    private static final String LEGACY_WCWT_NAMESPACE = "wireless_comprehensive_work_terminal";

    private WirelessEncodeTerminalItems() {
    }

    public static boolean mayProvideWirelessEncoding(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof WirelessTerminalItem) {
            return true;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) {
            return false;
        }
        if (WCWT_NAMESPACE.equals(key.getNamespace()) || LEGACY_WCWT_NAMESPACE.equals(key.getNamespace())) {
            return true;
        }
        String id = key.toString();
        return id.contains("pattern_encoding") || id.contains("universal_terminal");
    }
}
