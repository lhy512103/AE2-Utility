package com.lhy.ae2utility.api.recipe;

import java.util.Set;

import net.minecraft.resources.Identifier;

/** Recipe-finder classification extension point. */
public interface RecipeFinderApi {
    RecipeFinderApi INSTANCE = new RecipeFinderApiImpl();

    void registerClassifier(Identifier id, int priority, RecipeFeatureClassifier classifier);

    Set<String> classify(Object ingredient);

    String primaryFeature(Set<String> features);
}