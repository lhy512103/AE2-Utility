package com.lhy.ae2utility.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.lhy.ae2utility.api.card.NbtTearRule;

import net.minecraft.resources.ResourceLocation;

class Ae2UtilityApiTest {
    @Test
    void exposesCurrentApiVersion() {
        assertEquals(1, Ae2UtilityApi.API_VERSION);
    }

    @Test
    void nbtTearRuleCopiesItsInput() {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        ids.add(ResourceLocation.fromNamespaceAndPath("test", "first"));

        NbtTearRule rule = new NbtTearRule(ids);
        ids.add(ResourceLocation.fromNamespaceAndPath("test", "second"));

        assertEquals(1, rule.itemIds().size());
        assertThrows(UnsupportedOperationException.class,
                () -> rule.itemIds().add(ResourceLocation.fromNamespaceAndPath("test", "third")));
    }

    @Test
    void customRecipeFeaturesAreMergedWithBuiltIns() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "classifier_api_contract");
        Ae2UtilityApi.recipeFinder().registerClassifier(id, 100,
                ingredient -> "marker".equals(ingredient) ? Set.of("test.marker") : Set.of());

        Set<String> features = Ae2UtilityApi.recipeFinder().classify("marker");

        assertTrue(features.contains("test.marker"));
        assertTrue(features.contains("special"));
        assertThrows(IllegalArgumentException.class,
                () -> Ae2UtilityApi.recipeFinder().registerClassifier(id, 0, ignored -> Set.of()));
    }
}