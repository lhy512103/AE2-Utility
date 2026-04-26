package com.lhy.ae2utility.client.recipe_tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

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
        List<RequestedIngredient> rawLeaves = new ArrayList<>();
        root.collectLeaves(1, rawLeaves);

        Map<String, RequestedIngredient> merged = new LinkedHashMap<>();
        for (RequestedIngredient ingredient : rawLeaves) {
            String signature = signatureOf(ingredient);
            RequestedIngredient previous = merged.get(signature);
            if (previous == null) {
                merged.put(signature, ingredient.copy());
            } else {
                merged.put(signature, new RequestedIngredient(previous.alternatives(),
                        safeAdd(previous.count(), ingredient.count())));
            }
        }
        return List.copyOf(merged.values());
    }

    public List<RecipeTreeRecipeViewModel> collectSelectedRecipes() {
        List<RecipeTreeRecipeViewModel> raw = new ArrayList<>();
        root.collectSelectedRecipes(raw);

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

    private static int safeAdd(int left, int right) {
        long value = (long) left + (long) right;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, value));
    }

    private static String signatureOf(RequestedIngredient ingredient) {
        List<String> parts = new ArrayList<>();
        for (ItemStack alternative : ingredient.alternatives()) {
            if (!alternative.isEmpty()) {
                parts.add("itemtype#" + alternative.getItem());
            }
        }
        parts.sort(String::compareTo);
        return String.join("|", parts);
    }
}
