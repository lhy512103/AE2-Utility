package com.lhy.ae2utility.jei;

import org.jetbrains.annotations.Nullable;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.vanilla.IJeiIngredientInfoRecipe;
import net.minecraft.resources.ResourceLocation;

import com.lhy.ae2utility.integration.multiblock.MultiblockStructurePatternCompat;
import com.lhy.ae2utility.util.GenericIngredientUtil;

/**
 * JEI 也用 recipe layout 展示标签目录和物品信息页，它们不是可拉料或编码的配方。
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

    public static boolean isEncodableRecipeLayout(@Nullable IRecipeLayoutDrawable<?> layout) {
        if (layout == null || layout.getRecipeCategory() == null || isTagRecipeLayout(layout)) {
            return false;
        }
        var slots = layout.getRecipeSlotsView();
        if (!hasConvertibleIngredient(slots.getSlotViews(RecipeIngredientRole.INPUT))) {
            return false;
        }
        return hasConvertibleIngredient(slots.getSlotViews(RecipeIngredientRole.OUTPUT))
                || MultiblockStructurePatternCompat.isSupportedRecipe(layout.getRecipe(), true, false);
    }

    private static boolean hasConvertibleIngredient(java.util.List<IRecipeSlotView> slots) {
        for (IRecipeSlotView slot : slots) {
            for (var typed : slot.getAllIngredients().toList()) {
                if (GenericIngredientUtil.toAEKey(typed.getIngredient()) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isTagOrInformationRecipeClass(String className) {
        return className.startsWith("mezz.jei.library.plugins.jei.tags.")
                || className.startsWith("mezz.jei.library.plugins.jei.info.");
    }

    static boolean isTagRecipeType(@Nullable ResourceLocation uid) {
        return uid != null && "jei".equals(uid.getNamespace()) && uid.getPath().startsWith("tag_recipes/");
    }
}
