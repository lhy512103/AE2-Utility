package com.lhy.ae2utility.api.transfer;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/** One recipe slot and its acceptable item alternatives. */
public record IngredientRequest(List<ItemStack> alternatives, int count) {
    public IngredientRequest {
        if (alternatives == null) {
            throw new IllegalArgumentException("alternatives are required");
        }
        alternatives = alternatives.stream().map(ItemStack::copy).toList();
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
    }
}