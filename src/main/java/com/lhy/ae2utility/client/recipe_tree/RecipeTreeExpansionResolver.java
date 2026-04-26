package com.lhy.ae2utility.client.recipe_tree;

import java.util.List;

import com.lhy.ae2utility.jei.RecipeTreeJeiLookup;

public final class RecipeTreeExpansionResolver {
    private RecipeTreeExpansionResolver() {
    }

    public static List<RecipeTreeRecipeViewModel> resolveCandidates(RecipeTreeInputViewModel input) {
        return RecipeTreeJeiLookup.findRecipesByOutput(input.displayIngredient());
    }
}
