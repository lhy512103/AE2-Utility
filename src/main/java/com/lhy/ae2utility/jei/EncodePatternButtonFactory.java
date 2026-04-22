package com.lhy.ae2utility.jei;

import org.jetbrains.annotations.Nullable;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;

public class EncodePatternButtonFactory implements IRecipeButtonControllerFactory {
    @Nullable
    @Override
    public <T> IIconButtonController createButtonController(IRecipeLayoutDrawable<T> recipeLayoutDrawable) {
        return new EncodePatternButtonController(recipeLayoutDrawable);
    }
}
