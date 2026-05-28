package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import mezz.jei.gui.recipes.IRecipeGuiLogic;
import mezz.jei.gui.recipes.RecipeGuiLayouts;

@Mixin(targets = "mezz.jei.gui.recipes.RecipesGui", remap = false)
public interface RecipesGuiAccessor {

    @Accessor("layouts")
    RecipeGuiLayouts ae2utility$layouts();

    @Accessor("logic")
    IRecipeGuiLogic ae2utility$recipeLogic();
}
