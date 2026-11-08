package com.lhy.ae2utility.jei;

import org.jetbrains.annotations.Nullable;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.vanilla.IJeiIngredientInfoRecipe;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI also uses recipe layouts for tag catalogs and ingredient information pages.
 * Those pages are not recipes that can be pulled into an inventory or encoded.
 */
public final class JeiRecipePageKinds {
    private JeiRecipePageKinds() {
    }

    public static boolean isTagOrInformationRecipe(@Nullable Object recipe) {
        if (recipe instanceof IJeiIngredientInfoRecipe) {
            return true;
        }
        return recipe != null && isTagOrInformationRecipeClass(recipe.getClass().getName());
    }

    public static boolean isTagRecipeLayout(@Nullable IRecipeLayoutDrawable<?> layout) {
        if (layout == null || layout.getRecipeCategory() == null) {
            return false;
        }
        var recipeType = layout.getRecipeCategory().getRecipeType();
        if (recipeType == null) {
            return false;
        }
        return isTagRecipeType(recipeType.getUid()) || isTagOrInformationRecipe(layout.getRecipe());
    }

    static boolean isTagOrInformationRecipeClass(String className) {
        return className.startsWith("mezz.jei.library.plugins.jei.tags.")
                || className.startsWith("mezz.jei.library.plugins.jei.info.");
    }

    static boolean isTagRecipeType(@Nullable ResourceLocation uid) {
        return uid != null && "jei".equals(uid.getNamespace()) && uid.getPath().startsWith("tag_recipes/");
    }
}
