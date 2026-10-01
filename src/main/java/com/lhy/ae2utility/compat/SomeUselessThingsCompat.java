package com.lhy.ae2utility.compat;

import java.lang.reflect.Method;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.fml.ModList;

/** Optional bridge for Some Useless Things' omniversal pattern encoder.
 *
 * <p>The Forge 1.20.1 build does not compile against the optional mod. Reflection
 * keeps the bridge safe when the mod is absent while allowing compatible releases
 * to handle their own pattern format.</p>
 */
public final class SomeUselessThingsCompat {
    private static final String MOD_ID = "useless_mod";
    private static final String HANDLER = "com.sorrowmist.useless.compat.jei.OmniversalPatternJeiTransferHandler";
    private static final String RECIPE = "com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog$Entry";
    private static IRecipeTransferHandlerHelper transferHelper;
    private static Method transferMethod;

    private SomeUselessThingsCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static void prepare(IRecipeTransferHandlerHelper helper) {
        transferHelper = helper;
        transferMethod = null;
        if (!isLoaded() || helper == null) {
            return;
        }
        try {
            Class<?> handler = Class.forName(HANDLER);
            Class<?> recipe = Class.forName(RECIPE);
            for (Method method : handler.getMethods()) {
                if (!"transferOmniversalRecipe".equals(method.getName()) || method.getParameterCount() != 7) {
                    continue;
                }
                if (method.getParameterTypes()[1].isAssignableFrom(recipe)) {
                    transferMethod = method;
                    break;
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Optional compatibility must never prevent JEI from loading.
        }
    }

    public static boolean transfer(IRecipeLayoutDrawable<?> layout, boolean doTransfer) {
        if (layout == null || transferHelper == null || transferMethod == null || !isLoaded()) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.containerMenu == null
                || !"appeng.menu.me.items.PatternEncodingTermMenu".equals(player.containerMenu.getClass().getName())) {
            return false;
        }
        Object recipe = layout.getRecipe();
        if (recipe == null || !RECIPE.equals(recipe.getClass().getName())) {
            return false;
        }
        try {
            transferMethod.invoke(null, player.containerMenu, recipe, layout.getRecipeSlotsView(), player,
                    doTransfer, false, transferHelper);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }
}

