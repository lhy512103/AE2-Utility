package com.lhy.ae2utility.integration.ae2;

import net.minecraft.world.level.ItemLike;
import net.neoforged.fml.ModList;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;

import com.lhy.ae2utility.init.ModItems;

/**
 * 将撕裂卡注册到 AE2 的 Upgrades 系统，使其能放入受支持机器的升级槽。
 */
public final class PatternProviderTearStacks {
    private static final String EXTENDEDAE_SINGLETONS = "com.glodblock.github.extendedae.common.EAESingletons";
    private static final String AECS_BLOCKS = "io.github.lounode.ae2cs.common.init.AECSBlocks";
    private static final String AECS_PARTS = "io.github.lounode.ae2cs.common.init.AECSParts";
    private static final String ADVANCED_AE_BLOCKS = "net.pedroksl.advanced_ae.common.definitions.AAEBlocks";
    private static final String ADVANCED_AE_ITEMS = "net.pedroksl.advanced_ae.common.definitions.AAEItems";
    private static final String AE2_LIGHTNING_TECH_BLOCKS = "com.moakiee.ae2lt.registry.ModBlocks";
    private static final String AE2_PATTERN_PROVIDER_GROUP = "block.ae2.pattern_provider";
    private static final String EXTENDEDAE_PATTERN_PROVIDER_GROUP = "block.extendedae.ex_pattern_provider";
    private static final String AECS_SIMPLE_PATTERN_PROVIDER_GROUP = "block.ae2cs.simple_pattern_provider";
    private static final String AECS_RESONATING_PATTERN_PROVIDER_GROUP = "block.ae2cs.resonating_pattern_provider";
    private static final String AECS_EX_RESONATING_PATTERN_PROVIDER_GROUP = "block.ae2cs.extended_resonating_pattern_provider";
    private static final String AECS_METEORITE_PATTERN_PROVIDER_GROUP = "block.ae2cs.meteorite_pattern_provider";
    private static final String ADVANCED_AE_ADV_PATTERN_PROVIDER_GROUP = "block.advanced_ae.adv_pattern_provider";
    private static final String ADVANCED_AE_SMALL_ADV_PATTERN_PROVIDER_GROUP = "block.advanced_ae.small_adv_pattern_provider";
    private static final String AE2LT_OVERLOADED_PATTERN_PROVIDER_GROUP = "group.pattern_provider.name";

    private PatternProviderTearStacks() {
    }

    public static void registerTearCardAsPatternProviderUpgradeWhenApplicable() {
        registerCard(ModItems.NBT_TEAR_CARD.get(), false);
        registerCard(ModItems.REDSTONE_SIGNAL_CARD.get(), true);
    }

    private static void registerCard(ItemLike card, boolean supportAe2ltOverloadedProvider) {
        Upgrades.add(card, AEParts.PATTERN_PROVIDER, 1, AE2_PATTERN_PROVIDER_GROUP);
        Upgrades.add(card, AEBlocks.PATTERN_PROVIDER, 1, AE2_PATTERN_PROVIDER_GROUP);
        if (ModList.get().isLoaded("extendedae")) {
            tryRegisterExtendedAePatternProviders(card);
        }
        if (ModList.get().isLoaded("ae2cs")) {
            tryRegisterAe2CrystalSciencePatternProviders(card);
        }
        if (ModList.get().isLoaded("advanced_ae")) {
            tryRegisterAdvancedAePatternProviders(card);
        }
        if (supportAe2ltOverloadedProvider && ModList.get().isLoaded("ae2lt")) {
            tryRegisterAe2LightningTechPatternProviders(card);
        }
    }

    private static void tryRegisterExtendedAePatternProviders(ItemLike tear) {
        try {
            Class<?> c = Class.forName(EXTENDEDAE_SINGLETONS);
            ItemLike blockItem = (ItemLike) c.getField("EX_PATTERN_PROVIDER").get(null);
            ItemLike partItem = (ItemLike) c.getField("EX_PATTERN_PROVIDER_PART").get(null);
            Upgrades.add(tear, blockItem, 1, EXTENDEDAE_PATTERN_PROVIDER_GROUP);
            Upgrades.add(tear, partItem, 1, EXTENDEDAE_PATTERN_PROVIDER_GROUP);
        } catch (Throwable ignored) {
        }
    }

    private static void tryRegisterAe2CrystalSciencePatternProviders(ItemLike tear) {
        try {
            Class<?> blocks = Class.forName(AECS_BLOCKS);
            Class<?> parts = Class.forName(AECS_PARTS);
            registerField(tear, blocks, "SIMPLE_PATTERN_PROVIDER_BLOCK", AECS_SIMPLE_PATTERN_PROVIDER_GROUP);
            registerField(tear, parts, "SIMPLE_PATTERN_PROVIDER_PART", AECS_SIMPLE_PATTERN_PROVIDER_GROUP);
            registerField(tear, blocks, "RESONATING_PATTERN_PROVIDER_BLOCK", AECS_RESONATING_PATTERN_PROVIDER_GROUP);
            registerField(tear, parts, "RESONATING_PATTERN_PROVIDER_PART", AECS_RESONATING_PATTERN_PROVIDER_GROUP);
            registerField(tear, blocks, "EX_RESONATING_PATTERN_PROVIDER_BLOCK", AECS_EX_RESONATING_PATTERN_PROVIDER_GROUP);
            registerField(tear, parts, "EX_RESONATING_PATTERN_PROVIDER_PART", AECS_EX_RESONATING_PATTERN_PROVIDER_GROUP);
            registerField(tear, blocks, "METEORITE_PATTERN_PROVIDER_BLOCK", AECS_METEORITE_PATTERN_PROVIDER_GROUP);
            registerField(tear, parts, "METEORITE_PATTERN_PROVIDER_PART", AECS_METEORITE_PATTERN_PROVIDER_GROUP);
        } catch (Throwable ignored) {
        }
    }

    private static void tryRegisterAdvancedAePatternProviders(ItemLike tear) {
        try {
            Class<?> blocks = Class.forName(ADVANCED_AE_BLOCKS);
            Class<?> items = Class.forName(ADVANCED_AE_ITEMS);
            registerField(tear, blocks, "ADV_PATTERN_PROVIDER", ADVANCED_AE_ADV_PATTERN_PROVIDER_GROUP);
            registerField(tear, items, "ADV_PATTERN_PROVIDER", ADVANCED_AE_ADV_PATTERN_PROVIDER_GROUP);
            registerField(tear, blocks, "SMALL_ADV_PATTERN_PROVIDER", ADVANCED_AE_SMALL_ADV_PATTERN_PROVIDER_GROUP);
            registerField(tear, items, "SMALL_ADV_PATTERN_PROVIDER", ADVANCED_AE_SMALL_ADV_PATTERN_PROVIDER_GROUP);
        } catch (Throwable ignored) {
        }
    }

    private static void tryRegisterAe2LightningTechPatternProviders(ItemLike tear) {
        try {
            Class<?> blocks = Class.forName(AE2_LIGHTNING_TECH_BLOCKS);
            registerField(tear, blocks, "OVERLOADED_PATTERN_PROVIDER", AE2LT_OVERLOADED_PATTERN_PROVIDER_GROUP);
        } catch (Throwable ignored) {
        }
    }

    private static void registerField(ItemLike tear, Class<?> owner, String fieldName, String groupName) throws ReflectiveOperationException {
        ItemLike target = (ItemLike) owner.getField(fieldName).get(null);
        Upgrades.add(tear, target, 1, groupName);
    }
}
