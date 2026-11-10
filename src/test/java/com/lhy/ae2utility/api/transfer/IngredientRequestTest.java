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
    void callerAndReaderCannotMutateStoredStackSnapshot() {
        com.lhy.ae2utility.testutil.MinecraftTestBootstrap.ensure();
        ItemStack original = new ItemStack(net.minecraft.world.item.Items.STONE, 3);
        original.getOrCreateTag().putString("consumer", "original");
        IngredientRequest request = new IngredientRequest(List.of(original), 3);
        original.setCount(1);
        original.getOrCreateTag().putString("consumer", "caller mutation");
        ItemStack firstReader = request.alternatives().get(0);
        assertEquals(3, firstReader.getCount());
        assertEquals("original", firstReader.getOrCreateTag().getString("consumer"));
        firstReader.setCount(2);
        firstReader.getOrCreateTag().putString("consumer", "reader mutation");
        ItemStack secondReader = request.alternatives().get(0);
        assertEquals(3, secondReader.getCount());
        assertEquals("original", secondReader.getOrCreateTag().getString("consumer"));
    }

    @Test
    void rejectsNegativeCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> new IngredientRequest(List.of(), -1));
    }
}