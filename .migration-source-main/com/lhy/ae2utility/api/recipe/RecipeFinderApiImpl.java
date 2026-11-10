package com.lhy.ae2utility.api.recipe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lhy.ae2utility.recipe_finder.RecipeFinderFeatureClassifier;

import net.minecraft.resources.ResourceLocation;

final class RecipeFinderApiImpl implements RecipeFinderApi {
    private final Map<ResourceLocation, RegisteredClassifier> classifiers = new LinkedHashMap<>();

    @Override
    public synchronized void registerClassifier(ResourceLocation id, int priority, RecipeFeatureClassifier classifier) {
        if (id == null || classifier == null) {
            throw new IllegalArgumentException("id and classifier are required");
        }
        if (classifiers.containsKey(id)) {
            throw new IllegalArgumentException("recipe classifier already registered: " + id);
        }
        classifiers.put(id, new RegisteredClassifier(id, priority, classifier));
    }

    @Override
    public Set<String> classify(Object ingredient) {
        Set<String> features = new LinkedHashSet<>();
        for (RegisteredClassifier registered : orderedClassifiers()) {
            Set<String> classified = registered.classifier().classify(ingredient);
            if (classified != null) {
                classified.stream().filter(RecipeFinderApiImpl::validFeatureKey).forEach(features::add);
            }
        }
        features.addAll(RecipeFinderFeatureClassifier.classifyIngredient(ingredient));
        return Set.copyOf(features);
    }

    @Override
    public String primaryFeature(Set<String> features) {
        return RecipeFinderFeatureClassifier.primaryFeature(features == null ? Set.of() : features);
    }

    private synchronized List<RegisteredClassifier> orderedClassifiers() {
        List<RegisteredClassifier> ordered = new ArrayList<>(classifiers.values());
        ordered.sort(Comparator.comparingInt(RegisteredClassifier::priority).reversed()
                .thenComparing(entry -> entry.id().toString()));
        return List.copyOf(ordered);
    }

    private static boolean validFeatureKey(String key) {
        return key != null && !key.isBlank() && key.length() <= 64;
    }

    private record RegisteredClassifier(ResourceLocation id, int priority, RecipeFeatureClassifier classifier) {
    }
}