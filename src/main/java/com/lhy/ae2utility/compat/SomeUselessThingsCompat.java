package com.lhy.ae2utility.compat;

import com.sorrowmist.useless.compat.jei.OmniversalPatternJeiTransferHandler;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import appeng.menu.me.items.PatternEncodingTermMenu;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.fml.ModList;

/** Direct JEI bridge for Some Useless Things' omniversal patterns. */
public final class SomeUselessThingsCompat {
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
        return isLoaded() && recipe instanceof AlloyFurnaceRecipeCatalog.Entry;
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
        OmniversalPatternJeiTransferHandler.transferOmniversalRecipe(
                menu, (AlloyFurnaceRecipeCatalog.Entry) layout.getRecipe(), slots, player,
                doTransfer, false, transferHelper);
        return true;
    }
}
