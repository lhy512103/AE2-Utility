package com.lhy.ae2utility.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModContainer;

public final class Ae2UtilityClientSetup {
    private Ae2UtilityClientSetup() {
    }

    public static void registerConfigScreen(ModContainer container) {
        container.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new Ae2UtilityConfigScreen(parent)));
    }
}
