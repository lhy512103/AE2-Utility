package com.lhy.ae2utility.client.recipe_tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class RecipeTreeRootContext {
    private final RecipeTreeNodeViewModel root;
    private final @Nullable Screen returnScreen;
    private final Map<String, RecipeTreeRecipeViewModel> rememberedSelections = new HashMap<>();
    private boolean disableExistingPatternExpansion = true;

    public RecipeTreeRootContext(RecipeTreeNodeViewModel root, @Nullable Screen returnScreen) {
        this.root = root;
        this.returnScreen = returnScreen;
    }

    public RecipeTreeNodeViewModel root() {
        return root;
    }

    public @Nullable Screen returnScreen() {
        return returnScreen;
    }

    public Component title() {
        return root.recipe().title();
    }

    public List<RequestedIngredient> collectRequestedIngredients() {
        Map<String, RecipeTreeNodeViewModel.LeafAccumulator> merged = new LinkedHashMap<>();
        root.collectLeavesMerged(1, merged);
        List<RequestedIngredient> requestedIngredients = new java.util.ArrayList<>(merged.size());
        for (RecipeTreeNodeViewModel.LeafAccumulator accumulator : merged.values()) {
            requestedIngredients.add(accumulator.toRequestedIngredient());
        }
        return List.copyOf(requestedIngredients);
    }

    public List<RecipeTreeRecipeViewModel> collectSelectedRecipes() {
        List<RecipeTreeRecipeViewModel> raw = new ArrayList<>();
        collectSelectedRecipes(root, raw, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
        List<RecipeTreeRecipeViewModel> unique = new ArrayList<>();
        outer: for (RecipeTreeRecipeViewModel candidate : raw) {
            for (RecipeTreeRecipeViewModel existing : unique) {
                if (existing.sameRecipeAs(candidate)) {
                    continue outer;
                }
            }
            unique.add(candidate);
        }
        return List.copyOf(unique);
    }

    public void rememberSelection(String signature, RecipeTreeRecipeViewModel recipe) {
        if (signature == null || signature.isBlank() || recipe == null) {
            return;
        }
        rememberedSelections.put(signature, recipe);
    }

    public @Nullable RecipeTreeRecipeViewModel getRememberedSelection(String signature) {
        if (signature == null || signature.isBlank()) {
            return null;
        }
        return rememberedSelections.get(signature);
    }

    public void forgetSelection(String signature) {
        if (signature == null || signature.isBlank()) {
            return;
        }
        rememberedSelections.remove(signature);
    }

    public boolean disableExistingPatternExpansion() {
        return disableExistingPatternExpansion;
    }

    public void setDisableExistingPatternExpansion(boolean disableExistingPatternExpansion) {
        this.disableExistingPatternExpansion = disableExistingPatternExpansion;
    }

    private void collectSelectedRecipes(RecipeTreeNodeViewModel node, List<RecipeTreeRecipeViewModel> recipes,
            Set<RecipeTreeNodeViewModel> visitedNodes) {
        if (!visitedNodes.add(node)) {
            return;
        }
        recipes.add(node.recipe());
        for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
            RecipeTreeNodeViewModel child = input.child();
            if (child != null) {
                collectSelectedRecipes(child, recipes, visitedNodes);
            }
        }
    }
}
