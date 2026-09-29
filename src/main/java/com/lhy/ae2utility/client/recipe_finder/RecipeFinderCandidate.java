package com.lhy.ae2utility.client.recipe_finder;

import java.util.Set;

import net.minecraft.world.item.ItemStack;

import mezz.jei.api.recipe.category.IRecipeCategory;

public record RecipeFinderCandidate(
        String identityKey,
        ItemStack previewStack,
        String displayName,
        String sourceModId,
        String sourceModName,
        String machineLabel,
        Set<String> outputItemIds,
        Set<String> inputItemIds,
        Set<String> involvedModIds,
        Set<String> inputFeatureKeys,
        Set<String> outputFeatureKeys,
        boolean encodable,
        IRecipeCategory<?> category,
        Object recipe) {
}
