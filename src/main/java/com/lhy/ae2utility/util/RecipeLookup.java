package com.lhy.ae2utility.util;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/** Recipe lookup using server data or AE2's client-synchronized crafting recipes. */
public final class RecipeLookup {
    private RecipeLookup() {}

    public static @Nullable RecipeHolder<?> byId(@Nullable Level level, @Nullable Identifier id) {
        if (level == null || id == null) {
            return null;
        }
        var key = ResourceKey.create(Registries.RECIPE, id);
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.recipeAccess().byKey(key).orElse(null);
        }
        RecipeHolder<?> holder = appeng.crafting.RecipeAccess.byKey(level, RecipeType.CRAFTING, key);
        if (holder == null) {
            holder = appeng.crafting.RecipeAccess.byKey(level, RecipeType.SMITHING, key);
        }
        if (holder == null) {
            holder = appeng.crafting.RecipeAccess.byKey(level, RecipeType.STONECUTTING, key);
        }
        return holder;
    }

    public static boolean fitsCraftingGrid(CraftingRecipe recipe) {
        if (recipe instanceof ShapedRecipe shaped && (shaped.getWidth() > 3 || shaped.getHeight() > 3)) {
            return false;
        }
        return appeng.util.CraftingRecipeUtil.getIngredients(recipe).size() <= 9;
    }
}
