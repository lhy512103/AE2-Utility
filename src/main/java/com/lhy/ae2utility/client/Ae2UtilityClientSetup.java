package com.lhy.ae2utility.client;

import java.util.function.Function;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;

public final class Ae2UtilityClientSetup {
    private Ae2UtilityClientSetup() {
    }

    public static void init(ModContainer container, IEventBus modBus) {
        registerConfigScreen(container);
        modBus.addListener(Ae2UtilityClientSetup::registerTooltipComponents);
    }

    private static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(PatternEncodingPreviewTooltip.class, Function.identity());
    }

    private static void registerConfigScreen(ModContainer container) {
        container.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new Ae2UtilityConfigScreen(parent)));
    }
}
