package com.lhy.ae2utility.client.recipe_tree;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;

import net.minecraft.world.item.ItemStack;

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
            RequestedIngredient ingredient = input.selectedRequestedIngredient();
            int requiredAmount = safeMultiply(multiplier, input.amount());
            if (child != null) {
                int childCrafts = ceilDiv(requiredAmount, child.recipe.primaryOutputCount());
                child.collectLeaves(childCrafts, leaves);
            } else if (ingredient != null) {
                leaves.add(new RequestedIngredient(ingredient.alternatives(), requiredAmount));
            }
        }
    }

    public void collectLeavesMerged(int multiplier, Map<String, RequestedIngredient> mergedLeaves,
            Function<RequestedIngredient, String> signatureOf) {
        for (RecipeTreeInputViewModel input : recipe.inputs()) {
            RecipeTreeNodeViewModel child = input.child();
            RequestedIngredient ingredient = input.selectedRequestedIngredient();
            int requiredAmount = safeMultiply(multiplier, input.amount());
            if (child != null) {
                int childCrafts = ceilDiv(requiredAmount, child.recipe.primaryOutputCount());
                child.collectLeavesMerged(childCrafts, mergedLeaves, signatureOf);
            } else if (ingredient != null) {
                String signature = signatureOf.apply(ingredient);
                RequestedIngredient previous = mergedLeaves.get(signature);
                if (previous == null) {
                    mergedLeaves.put(signature, new RequestedIngredient(ingredient.alternatives(), requiredAmount));
                } else {
                    mergedLeaves.put(signature, new RequestedIngredient(previous.alternatives(),
                            safeAdd(previous.count(), requiredAmount)));
                }
            }
        }
    }

    public void collectLeavesMerged(int multiplier, Map<String, LeafAccumulator> mergedLeaves) {
        Map<RecipeTreeNodeViewModel, Integer> aggregatedChildConsumption = new LinkedHashMap<>();
        for (RecipeTreeInputViewModel input : recipe.inputs()) {
            RecipeTreeNodeViewModel child = input.child();
            int requiredAmount = safeMultiply(multiplier, input.amount());
            if (child != null) {
                aggregatedChildConsumption.merge(child, requiredAmount, RecipeTreeNodeViewModel::safeAdd);
                continue;
            }

            String signature = input.requestedIngredientSignature();
            if (signature == null || signature.isBlank()) {
                continue;
            }

            LeafAccumulator previous = mergedLeaves.get(signature);
            if (previous == null) {
                mergedLeaves.put(signature, new LeafAccumulator(input.orderedAlternativesView(), requiredAmount));
            } else {
                previous.add(requiredAmount);
            }
        }

        for (Map.Entry<RecipeTreeNodeViewModel, Integer> entry : aggregatedChildConsumption.entrySet()) {
            RecipeTreeNodeViewModel child = entry.getKey();
            int childCrafts = ceilDiv(entry.getValue(), child.recipe.primaryOutputCount());
            child.collectLeavesMerged(childCrafts, mergedLeaves);
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

    private static int safeAdd(int left, int right) {
        long value = (long) left + (long) right;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, value));
    }

    private static int ceilDiv(int numerator, int denominator) {
        if (denominator <= 0) {
            return numerator;
        }
        return (numerator + denominator - 1) / denominator;
    }

    public static final class LeafAccumulator {
        private final List<ItemStack> alternatives;
        private int count;

        public LeafAccumulator(List<ItemStack> alternatives, int count) {
            this.alternatives = List.copyOf(alternatives);
            this.count = Math.max(1, count);
        }

        public List<ItemStack> alternatives() {
            return alternatives;
        }

        public int count() {
            return count;
        }

        public void add(int amount) {
            this.count = safeAdd(this.count, amount);
        }

        public RequestedIngredient toRequestedIngredient() {
            return new RequestedIngredient(alternatives, count);
        }
    }
}
