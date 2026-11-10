package com.lhy.ae2utility.init;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import appeng.api.features.GridLinkables;

import com.lhy.ae2utility.integration.ae2.PatternProviderTearStacks;
import com.lhy.ae2utility.item.MeQuickTransferToolItem;
import com.lhy.ae2utility.service.MeQuickTransferService;

public final class ModCommonSetup {
    private ModCommonSetup() {
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(PatternProviderTearStacks::registerTearCardAsPatternProviderUpgradeWhenApplicable);
        event.enqueueWork(() -> GridLinkables.register(ModItems.ME_QUICK_TRANSFER,
                MeQuickTransferToolItem.LINKABLE_HANDLER));
        MeQuickTransferService.registerTickHandler();
    }
}
