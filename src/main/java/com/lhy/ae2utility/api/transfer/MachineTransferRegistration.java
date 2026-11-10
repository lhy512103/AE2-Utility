package com.lhy.ae2utility.api.transfer;

import java.util.Arrays;

import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Client registration used to add JEI transfer support for a machine menu. */
public record MachineTransferRegistration(
        Identifier id,
        Class<? extends AbstractContainerMenu> menuClass,
        IRecipeType<?> recipeType,
        int[] inputSlotIndices) {

    public MachineTransferRegistration {
        if (id == null || menuClass == null || recipeType == null || inputSlotIndices == null) {
            throw new IllegalArgumentException("all machine transfer fields are required");
        }
        inputSlotIndices = inputSlotIndices.clone();
        if (inputSlotIndices.length == 0 || Arrays.stream(inputSlotIndices).anyMatch(index -> index < 0)) {
            throw new IllegalArgumentException("at least one non-negative input slot is required");
        }
    }

    @Override
    public int[] inputSlotIndices() {
        return inputSlotIndices.clone();
    }
}