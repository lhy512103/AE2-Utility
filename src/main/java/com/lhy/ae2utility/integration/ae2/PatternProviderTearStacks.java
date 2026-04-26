package com.lhy.ae2utility.integration.ae2;

import java.lang.reflect.Field;

import net.minecraft.world.level.ItemLike;
import net.neoforged.fml.ModList;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;

import com.lhy.ae2utility.init.ModItems;

/**
 * 将撕裂卡注册到 AE2 的 Upgrades 系统，使其能放入样板供应器的任意升级槽。
 * <p>
 * “最多 1 张”的限制不再依赖这里的注册本身，而由菜单校验统一保证整个供应器只能存在一张撕裂卡。
 */
public final class PatternProviderTearStacks {
    private static final String EXTENDEDAE_SINGLETONS = "com.glodblock.github.extendedae.common.EAESingletons";
    private static final String PATTERN_PROVIDER_GROUP = "group.pattern_provider.name";

    private PatternProviderTearStacks() {
    }

    public static void registerTearCardAsPatternProviderUpgradeWhenApplicable() {
        if (!ModList.get().isLoaded("extendedae_plus")) {
            return;
        }
        ItemLike tear = ModItems.NBT_TEAR_CARD.get();
        Upgrades.add(tear, AEParts.PATTERN_PROVIDER, 1, PATTERN_PROVIDER_GROUP);
        Upgrades.add(tear, AEBlocks.PATTERN_PROVIDER, 1, PATTERN_PROVIDER_GROUP);
        if (ModList.get().isLoaded("extendedae")) {
            tryRegisterExtendedAePatternProviders(tear);
        }
    }

    private static void tryRegisterExtendedAePatternProviders(ItemLike tear) {
        try {
            Class<?> c = Class.forName(EXTENDEDAE_SINGLETONS);
            Field exBlock = c.getField("EX_PATTERN_PROVIDER");
            Field exPart = c.getField("EX_PATTERN_PROVIDER_PART");
            ItemLike blockItem = (ItemLike) exBlock.get(null);
            ItemLike partItem = (ItemLike) exPart.get(null);
            Upgrades.add(tear, blockItem, 1, PATTERN_PROVIDER_GROUP);
            Upgrades.add(tear, partItem, 1, PATTERN_PROVIDER_GROUP);
        } catch (Throwable ignored) {
        }
    }
}
