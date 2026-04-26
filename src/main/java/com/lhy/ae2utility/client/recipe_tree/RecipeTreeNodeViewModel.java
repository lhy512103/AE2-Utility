package com.lhy.ae2utility.client.recipe_tree;

import java.util.List;

import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;

public final class RecipeTreeNodeViewModel {
    private RecipeTreeRecipeViewModel recipe;
    private final RecipeTreeNodeViewModel parent;

    public RecipeTreeNodeViewModel(RecipeTreeRecipeViewModel recipe, RecipeTreeNodeViewModel parent) {
        this.recipe = recipe;
        this.parent = parent;
    }

    public RecipeTreeRecipeViewModel recipe() {
        return recipe;
    }

    public void setRecipe(RecipeTreeRecipeViewModel recipe) {
        if (recipe != null) {
            this.recipe = recipe;
        }
    }

    public RecipeTreeNodeViewModel parent() {
        return parent;
    }

    public boolean containsRecipe(RecipeTreeRecipeViewModel candidate) {
        for (RecipeTreeNodeViewModel node = this; node != null; node = node.parent) {
            if (node.recipe.sameRecipeAs(candidate)) {
                return true;
            }
        }
        return false;
    }

    public void collectLeaves(int multiplier, List<RequestedIngredient> leaves) {
        for (RecipeTreeInputViewModel input : recipe.inputs()) {
            RecipeTreeNodeViewModel child = input.child();
            RequestedIngredient ingredient = input.requestedIngredient();
            int requiredAmount = safeMultiply(multiplier, input.amount());
            if (child != null) {
                int childCrafts = ceilDiv(requiredAmount, child.recipe.primaryOutputCount());
                child.collectLeaves(childCrafts, leaves);
            } else if (ingredient != null) {
                leaves.add(new RequestedIngredient(ingredient.alternatives(), requiredAmount));
            }
        }
    }

    public void collectSelectedRecipes(List<RecipeTreeRecipeViewModel> recipes) {
        recipes.add(recipe);
        for (RecipeTreeInputViewModel input : recipe.inputs()) {
            RecipeTreeNodeViewModel child = input.child();
            if (child != null) {
                child.collectSelectedRecipes(recipes);
            }
        }
    }

    private static int safeMultiply(int left, int right) {
        long value = (long) left * (long) right;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, value));
    }

    private static int ceilDiv(int numerator, int denominator) {
        if (denominator <= 0) {
            return numerator;
        }
        return (numerator + denominator - 1) / denominator;
    }
}
