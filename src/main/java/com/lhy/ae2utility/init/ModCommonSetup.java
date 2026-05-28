package com.lhy.ae2utility.init;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.integration.ae2.PatternProviderTearStacks;

public final class ModCommonSetup {
    private ModCommonSetup() {
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PatternProviderTearStacks.registerTearCardAsPatternProviderUpgradeWhenApplicable();
            Ae2UtilityMod.LOGGER.info(
                    "[AE2UTILITY_DEBUG][startup_probe] mod={} redstoneCard={} tearCard={} thread={}",
                    Ae2UtilityMod.MOD_ID,
                    ModItems.REDSTONE_SIGNAL_CARD.getId(),
                    ModItems.NBT_TEAR_CARD.getId(),
                    Thread.currentThread().getName());
        });
    }
}
