package com.lhy.ae2utility.api.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;

class IngredientRequestTest {
    @Test
    void copiesAndFreezesAlternativeList() {
        List<ItemStack> alternatives = new ArrayList<>();

        IngredientRequest request = new IngredientRequest(alternatives, 2);
        alternatives.add(null);

        assertEquals(0, request.alternatives().size());
        assertThrows(UnsupportedOperationException.class,
                () -> request.alternatives().add(null));
    }

    @Test
    void rejectsNegativeCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> new IngredientRequest(List.of(), -1));
    }
}