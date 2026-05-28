package com.lhy.ae2utility.client;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import com.lhy.ae2utility.init.ModMenus;
import com.lhy.ae2utility.menu.NbtTearCardMenu;
import com.lhy.ae2utility.menu.RecipeFinderMenu;

public final class ModClientSetup {
    private ModClientSetup() {
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.NBT_TEAR_CARD.get(), NbtTearCardScreen::new);
        event.register(ModMenus.RECIPE_FINDER.get(), RecipeFinderScreen::new);
    }
}
