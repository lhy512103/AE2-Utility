package com.lhy.ae2utility.api.transfer;

import java.util.Arrays;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Common machine menu and input-slot metadata, safe to load without recipe viewers. */
public record MachineTransferRegistration(
        ResourceLocation id,
        Class<? extends AbstractContainerMenu> menuClass,
        int[] inputSlotIndices) {

    public MachineTransferRegistration {
        if (id == null || menuClass == null || inputSlotIndices == null) {
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
