package com.lhy.ae2utility.jei;

import net.minecraft.resources.ResourceLocation;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.compat.JeictCompat;
import com.lhy.ae2utility.client.RecipeFinderScreen;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;

/**
 * 1.20.1 forge JEI 插件。
 *
 * <p>JEI 15.20.x for Forge 1.20.1 does not expose the 1.21 global recipe-button factory API.
 * The encode arrow is rendered by {@link JeiEncodeButtonOverlay} against JEI's visible recipe layouts.</p>
 */
@JeiPlugin
public class Ae2UtilityJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(Ae2UtilityMod.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(RecipeFinderScreen.class, new RecipeFinderGhostHandler());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        EncodePatternButtonState.setJeiRuntime(jeiRuntime);
        JeictCompat.tryRegisterBackend();
    }

    @Override
    public void onRuntimeUnavailable() {
        EncodePatternButtonState.clearJeiRuntime();
    }
}
