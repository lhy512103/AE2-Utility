package com.lhy.ae2utility.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import mezz.jei.gui.recipes.RecipeGuiLayouts;

@Mixin(targets = "mezz.jei.gui.recipes.RecipeGuiLayouts", remap = false)
public interface RecipeGuiLayoutsAccessor {

    @Accessor("recipeLayoutsWithButtons")
    List<?> ae2utility$recipeLayoutsWithButtons();
}
