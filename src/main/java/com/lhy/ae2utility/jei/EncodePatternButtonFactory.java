package com.lhy.ae2utility.jei;

import org.jetbrains.annotations.Nullable;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import net.minecraft.resources.ResourceLocation;

public class EncodePatternButtonFactory implements IRecipeButtonControllerFactory {
    @Nullable
    @Override
    public <T> IIconButtonController createButtonController(IRecipeLayoutDrawable<T> recipeLayoutDrawable) {
        if (isTagRecipeLayout(recipeLayoutDrawable)) {
            return null;
        }
        return new EncodePatternButtonController(recipeLayoutDrawable);
    }

    private static boolean isTagRecipeLayout(IRecipeLayoutDrawable<?> recipeLayoutDrawable) {
        if (recipeLayoutDrawable == null || recipeLayoutDrawable.getRecipeCategory() == null) {
            return false;
        }
        var recipeType = recipeLayoutDrawable.getRecipeCategory().getRecipeType();
        if (recipeType == null) {
            return false;
        }
        ResourceLocation uid = recipeType.getUid();
        return uid != null && "jei".equals(uid.getNamespace()) && uid.getPath().startsWith("tag_recipes/");
    }
}
