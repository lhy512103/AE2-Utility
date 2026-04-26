package com.lhy.ae2utility.jei;

import com.lhy.ae2utility.client.RecipeTreeScreen;
import com.lhy.ae2utility.client.recipe_tree.RecipeTreeNodeViewModel;
import com.lhy.ae2utility.client.recipe_tree.RecipeTreeRootContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;

public final class RecipeTreeOpenHelper {
    private RecipeTreeOpenHelper() {
    }

    public static void openFromLayout(IRecipeLayoutDrawable<?> recipeLayout, Screen returnScreen) {
        open(recipeLayout.getRecipe(), recipeLayout.getRecipeSlotsView(), returnScreen);
    }

    public static void open(Object recipe, IRecipeSlotsView recipeSlots, Screen returnScreen) {
        var rootRecipe = RecipeTreeJeiLookup.createRootSnapshot(recipe, recipeSlots);
        Minecraft.getInstance().setScreen(new RecipeTreeScreen(
                new RecipeTreeRootContext(new RecipeTreeNodeViewModel(rootRecipe, null), returnScreen)));
    }
}
