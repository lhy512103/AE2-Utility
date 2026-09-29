package com.lhy.ae2utility.init;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ModSetup {
    private ModSetup() {
    }

    public static void register(IEventBus modBus) {
        ModItems.REG.register(modBus);
        ModMenus.REG.register(modBus);
        modBus.addListener(ModSetup::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            ModItems.creativeTabItems().forEach(item -> event.accept(item.get()));
        }
    }
}
