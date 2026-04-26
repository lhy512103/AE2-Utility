package com.lhy.ae2utility.client.recipe_tree;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class RecipeTreeRecipeViewModel {
    private final @Nullable ITypedIngredient<?> primaryOutputIngredient;
    private final ItemStack primaryOutput;
    private final int primaryOutputAmount;
    private final Component title;
    private final @Nullable Component subtitle;
    private final @Nullable IDrawable subtitleIcon;
    private final @Nullable ResourceLocation recipeId;
    private final List<RecipeTreeInputViewModel> inputs;

    public RecipeTreeRecipeViewModel(@Nullable ITypedIngredient<?> primaryOutputIngredient, ItemStack primaryOutput, int primaryOutputAmount,
            Component title, @Nullable Component subtitle, @Nullable IDrawable subtitleIcon, @Nullable ResourceLocation recipeId,
            List<RecipeTreeInputViewModel> inputs) {
        this.primaryOutputIngredient = primaryOutputIngredient;
        this.primaryOutput = primaryOutput.copy();
        this.primaryOutputAmount = Math.max(1, primaryOutputAmount);
        this.title = title.copy();
        this.subtitle = subtitle == null ? null : subtitle.copy();
        this.subtitleIcon = subtitleIcon;
        this.recipeId = recipeId;
        this.inputs = List.copyOf(new ArrayList<>(inputs));
    }

    public @Nullable ITypedIngredient<?> primaryOutputIngredient() {
        return primaryOutputIngredient;
    }

    public ItemStack primaryOutput() {
        return primaryOutput.copy();
    }

    public Component title() {
        return title.copy();
    }

    public @Nullable Component subtitle() {
        return subtitle == null ? null : subtitle.copy();
    }

    public @Nullable IDrawable subtitleIcon() {
        return subtitleIcon;
    }

    public @Nullable ResourceLocation recipeId() {
        return recipeId;
    }

    public List<RecipeTreeInputViewModel> inputs() {
        return inputs;
    }

    public int primaryOutputCount() {
        return primaryOutputAmount;
    }

    public boolean sameRecipeAs(RecipeTreeRecipeViewModel other) {
        if (recipeId != null && other.recipeId != null) {
            return recipeId.equals(other.recipeId);
        }
        return ItemStack.isSameItemSameComponents(primaryOutput, other.primaryOutput)
                && title.getString().equals(other.title.getString());
    }
}
