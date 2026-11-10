package com.lhy.ae2utility.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.lhy.ae2utility.api.card.NbtTearRule;

import net.minecraft.resources.Identifier;

class Ae2UtilityApiTest {
    @Test
    void exposesCurrentApiVersion() {
        assertEquals(2, Ae2UtilityApi.API_VERSION);
    }

    @Test
    void nbtTearRuleCopiesItsInput() {
        Set<Identifier> ids = new LinkedHashSet<>();
        ids.add(Identifier.fromNamespaceAndPath("test", "first"));

        NbtTearRule rule = new NbtTearRule(ids);
        ids.add(Identifier.fromNamespaceAndPath("test", "second"));

        assertEquals(1, rule.itemIds().size());
        assertThrows(UnsupportedOperationException.class,
                () -> rule.itemIds().add(Identifier.fromNamespaceAndPath("test", "third")));
    }
}
