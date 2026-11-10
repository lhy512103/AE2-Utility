package com.lhy.ae2utility.client;

import java.util.List;

import com.lhy.ae2utility.client.recipe_finder.RecipeFinderJeiIndexer;
import com.lhy.ae2utility.jei.Ae2UtilityJeiPlugin;
import com.lhy.ae2utility.recipe_finder.RecipeFinderCandidateView;

public final class RecipeFinderClientState {
    private static Object runtimeMarker;
    private static int version;
    private static List<RecipeFinderCandidateView> cachedRecipes = List.of();
    private static String statusMessage = "";

    private RecipeFinderClientState() {
    }

    public static Snapshot getSnapshot() {
        Object runtime = Ae2UtilityJeiPlugin.getJeiRuntime();
        if (runtime == null) {
            runtimeMarker = null;
            cachedRecipes = List.of();
            statusMessage = "JEI 未就绪，无法读取全局配方。";
            return new Snapshot(version, cachedRecipes, statusMessage);
        }

        if (runtimeMarker != runtime || cachedRecipes.isEmpty()) {
            rebuild();
        }
        return new Snapshot(version, cachedRecipes, statusMessage);
    }

    public static Snapshot rebuild() {
        runtimeMarker = Ae2UtilityJeiPlugin.getJeiRuntime();
        RecipeFinderJeiIndexer.BuildResult result = RecipeFinderJeiIndexer.build();
        cachedRecipes = result.recipes();
        statusMessage = result.statusMessage();
        version++;
        return new Snapshot(version, cachedRecipes, statusMessage);
    }

    public static void clear(int menuId) {
        // Intentionally kept for menu lifecycle symmetry; the recipe index is global per-client session.
    }

    public record Snapshot(int version, List<RecipeFinderCandidateView> recipes, String statusMessage) {
    }
}
