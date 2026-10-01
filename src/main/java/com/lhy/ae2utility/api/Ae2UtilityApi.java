package com.lhy.ae2utility.api;

import com.lhy.ae2utility.api.recipe.RecipeFinderApi;

/** Stable public entry point for AE2 Utility integrations. */
public final class Ae2UtilityApi {
    public static final int API_VERSION = 1;

    private Ae2UtilityApi() {
    }

    public static RecipeFinderApi recipeFinder() {
        return RecipeFinderApi.INSTANCE;
    }
}
