package com.lhy.ae2utility.api.recipe;

import java.util.Set;

/** Adds feature keys for an ingredient shown by the recipe finder. */
@FunctionalInterface
public interface RecipeFeatureClassifier {
    Set<String> classify(Object ingredient);
}