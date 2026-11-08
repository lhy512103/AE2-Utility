package com.lhy.ae2utility.client;

import java.util.Objects;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import com.lhy.ae2utility.init.ModMenus;

public final class Ae2UtilityClientSetup {
    private Ae2UtilityClientSetup() {
    }

    public static void init(ModContainer container, IEventBus modBus) {
        registerConfigScreen(container);
        modBus.addListener(Ae2UtilityClientSetup::registerTooltipComponents);
        modBus.addListener(Ae2UtilityClientSetup::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(Objects.requireNonNull(ModMenus.RECIPE_FINDER.get()), RecipeFinderScreen::new);
            MenuScreens.register(Objects.requireNonNull(ModMenus.NBT_TEAR_CARD.get()), NbtTearCardScreen::new);
        });
    }

    private static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(PatternEncodingPreviewTooltip.class, tooltip -> tooltip);
    }

    private static void registerConfigScreen(ModContainer container) {
        container.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new Ae2UtilityConfigScreen(parent)));
    }
}
