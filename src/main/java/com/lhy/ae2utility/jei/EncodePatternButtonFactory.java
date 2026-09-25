package com.lhy.ae2utility.jei;

import org.jetbrains.annotations.Nullable;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import net.minecraft.resources.ResourceLocation;

import com.lhy.ae2utility.util.GenericIngredientUtil;

public class EncodePatternButtonFactory implements IRecipeButtonControllerFactory {
    @Nullable
    @Override
    public <T> IIconButtonController createButtonController(IRecipeLayoutDrawable<T> recipeLayoutDrawable) {
        if (!isEncodableRecipeLayout(recipeLayoutDrawable)) {
            return null;
        }
        return new EncodePatternButtonController(recipeLayoutDrawable);
    }

    /**
     * JEI also uses recipe layouts for information pages, tag views, and other
     * pages that do not describe a pattern with inputs and an output. Creating
     * an encode controller for those layouts makes every page perform the
     * terminal/craftable checks during button updates for no useful action.
     */
    private static boolean isEncodableRecipeLayout(IRecipeLayoutDrawable<?> layout) {
        if (layout == null || layout.getRecipeCategory() == null || isTagRecipeLayout(layout)) {
            return false;
        }

        var slots = layout.getRecipeSlotsView();
        return hasIngredient(slots.getSlotViews(RecipeIngredientRole.INPUT))
                && hasIngredient(slots.getSlotViews(RecipeIngredientRole.OUTPUT));
    }

    private static boolean hasIngredient(java.util.List<IRecipeSlotView> slots) {
        for (IRecipeSlotView slot : slots) {
            for (var typed : slot.getAllIngredients().toList()) {
                if (GenericIngredientUtil.toAEKey(typed.getIngredient()) != null) {
                    return true;
                }
            }
        }
        return false;
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
