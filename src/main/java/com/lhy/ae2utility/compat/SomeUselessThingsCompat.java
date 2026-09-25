package com.lhy.ae2utility.compat;

import appeng.menu.me.items.PatternEncodingTermMenu;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

/** Direct JEI bridge for Some Useless Things' omniversal patterns. */
public final class SomeUselessThingsCompat {
    private static final String ENTRY_CLASS = "com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog$Entry";
    private static final String HANDLER_CLASS = "com.sorrowmist.useless.compat.jei.OmniversalPatternJeiTransferHandler";
    private static IRecipeTransferHandlerHelper transferHelper;

    private SomeUselessThingsCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("useless_mod");
    }

    public static void prepare(IRecipeTransferHandlerHelper helper) {
        if (!isLoaded()) {
            return;
        }
        transferHelper = helper;
    }

    public static boolean isOmniversalRecipe(Object recipe) {
        if (!isLoaded() || recipe == null) {
            return false;
        }
        try {
            return Class.forName(ENTRY_CLASS, false, recipe.getClass().getClassLoader()).isInstance(recipe);
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public static boolean transfer(IRecipeLayoutDrawable<?> layout, boolean doTransfer) {
        if (transferHelper == null || !isOmniversalRecipe(layout.getRecipe())) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(player.containerMenu instanceof PatternEncodingTermMenu menu)) {
            return false;
        }
        IRecipeSlotsView slots = layout.getRecipeSlotsView();
        try {
            ClassLoader loader = layout.getRecipe().getClass().getClassLoader();
            Class<?> entryClass = Class.forName(ENTRY_CLASS, false, loader);
            Class<?> handlerClass = Class.forName(HANDLER_CLASS, false, loader);
            handlerClass.getMethod("transferOmniversalRecipe", PatternEncodingTermMenu.class,
                    entryClass, IRecipeSlotsView.class, Player.class, boolean.class, boolean.class,
                    IRecipeTransferHandlerHelper.class)
                    .invoke(null, menu, layout.getRecipe(), slots, player, doTransfer, false, transferHelper);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
