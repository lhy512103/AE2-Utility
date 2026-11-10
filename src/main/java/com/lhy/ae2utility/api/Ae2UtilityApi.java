package com.lhy.ae2utility.api;

import com.lhy.ae2utility.api.card.CardApi;
import com.lhy.ae2utility.api.pattern.PatternEncodingApi;
import com.lhy.ae2utility.api.recipe.RecipeFinderApi;
import com.lhy.ae2utility.api.transfer.RecipeTransferApi;

/**
 * Stable entry point for integrations with AE2: Utility.
 *
 * <p>Only types below {@code com.lhy.ae2utility.api} are covered by the API
 * compatibility policy. Internal service, network and mixin packages may change
 * without notice.</p>
 */
public final class Ae2UtilityApi {
    public static final int API_VERSION = 1;

    private Ae2UtilityApi() {
    }

    public static PatternEncodingApi patternEncoding() {
        return PatternEncodingApi.INSTANCE;
    }

    public static RecipeTransferApi recipeTransfer() {
        return RecipeTransferApi.INSTANCE;
    }

    public static CardApi cards() {
        return CardApi.INSTANCE;
    }

    public static RecipeFinderApi recipeFinder() {
        return RecipeFinderApi.INSTANCE;
    }
}