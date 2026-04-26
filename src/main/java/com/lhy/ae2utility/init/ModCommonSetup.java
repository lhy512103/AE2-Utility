package com.lhy.ae2utility.init;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import com.lhy.ae2utility.integration.ae2.PatternProviderTearStacks;

public final class ModCommonSetup {
    private ModCommonSetup() {
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(PatternProviderTearStacks::registerTearCardAsPatternProviderUpgradeWhenApplicable);
    }
}
