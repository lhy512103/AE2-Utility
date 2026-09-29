package com.lhy.ae2utility.client.recipe_finder;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

import com.lhy.ae2utility.jei.EncodePatternButtonState;

/** 客户端全局缓存的配方索引与界面筛选状态，JEI 重载后自动重建。 */
public final class RecipeFinderClientState {
    private static Object runtimeMarker;
    private static int version;
    private static List<RecipeFinderCandidate> cachedRecipes = List.of();
    private static Component status = Component.empty();

    public static String modValue = "all";
    public static String machineValue = "all";
    public static String materialValue = "all";
    public static String outputValue = "all";
    public static String excludeTagValue = "all";
    public static boolean encodableOnly;
    public static String keyword = "";

    private RecipeFinderClientState() {
    }

    public static Snapshot getSnapshot() {
        Object runtime = ModList.get().isLoaded("jei") ? EncodePatternButtonState.getJeiRuntime() : null;
        if (runtime == null) {
            if (runtimeMarker != null || !cachedRecipes.isEmpty()) {
                runtimeMarker = null;
                cachedRecipes = List.of();
                version++;
            }
            status = Component.translatable("gui.ae2utility.recipe_finder.jei_unavailable");
            return new Snapshot(version, cachedRecipes, status);
        }
        if (runtimeMarker != runtime) {
            return rebuild();
        }
        return new Snapshot(version, cachedRecipes, status);
    }

    public static Snapshot rebuild() {
        if (!ModList.get().isLoaded("jei")) {
            return getSnapshot();
        }
        runtimeMarker = EncodePatternButtonState.getJeiRuntime();
        RecipeFinderJeiIndexer.BuildResult result = RecipeFinderJeiIndexer.build();
        cachedRecipes = result.recipes();
        status = result.status();
        version++;
        return new Snapshot(version, cachedRecipes, status);
    }

    public record Snapshot(int version, List<RecipeFinderCandidate> recipes, Component status) {
    }
}
