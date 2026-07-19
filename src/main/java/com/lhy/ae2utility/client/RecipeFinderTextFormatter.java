package com.lhy.ae2utility.client;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import com.lhy.ae2utility.recipe_finder.RecipeFinderFeatureClassifier;

/** Formatting-only helpers extracted from the recipe finder screen. */
public final class RecipeFinderTextFormatter {
    private RecipeFinderTextFormatter() {
    }

    public static String ellipsize(String value, int max) {
        if (max <= 0) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 2)) + "..";
    }

    public static String featureSummary(Set<String> features, Function<String, String> labeler, String emptyLabel) {
        return features.stream().map(labeler).limit(5).reduce((a, b) -> a + ", ").orElse(emptyLabel);
    }

    public static String sampleSummary(ItemStack sample, Function<String, String> labeler) {
        String modId = BuiltInRegistries.ITEM.getKey(sample.getItem()).getNamespace();
        String modName = RecipeFinderFeatureClassifier.modDisplayName(modId);
        Set<String> features = RecipeFinderFeatureClassifier.classifyItemStack(sample).stream()
                .filter(f -> !"other".equals(f)).collect(Collectors.toSet());
        if (features.isEmpty()) {
            return "样本模组: " + modName;
        }
        String featText = features.stream().map(labeler).collect(Collectors.joining(", "));
        return "样本: " + modName + " / " + featText;
    }
}
