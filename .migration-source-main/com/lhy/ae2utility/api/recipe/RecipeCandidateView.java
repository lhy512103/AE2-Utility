package com.lhy.ae2utility.api.recipe;

import java.util.List;
import java.util.Set;

import net.minecraft.world.item.ItemStack;

/** Read-only recipe-finder projection without internal packets or JEI objects. */
public interface RecipeCandidateView {
    String identityKey();

    ItemStack previewStack();

    String displayName();

    String sourceModId();

    String sourceModName();

    String machineKey();

    String machineLabel();

    String recipeId();

    Set<String> inputFeatureKeys();

    Set<String> outputFeatureKeys();

    List<String> inputDisplayNames();

    List<String> extraOutputDisplayNames();

    boolean encodable();
}