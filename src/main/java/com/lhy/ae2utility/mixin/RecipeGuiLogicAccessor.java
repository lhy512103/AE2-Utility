package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import mezz.jei.gui.recipes.RecipeGuiLogic;
import mezz.jei.gui.recipes.lookups.ILookupState;

@Mixin(targets = "mezz.jei.gui.recipes.RecipeGuiLogic", remap = false)
public interface RecipeGuiLogicAccessor {

    @Accessor("state")
    ILookupState ae2utility$lookupState();
}
