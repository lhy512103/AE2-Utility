package com.lhy.ae2utility.machine;

import java.util.Arrays;

import net.minecraft.world.inventory.AbstractContainerMenu;

import mezz.jei.api.recipe.RecipeType;

public final class MachineTransferProfile {
    private final String id;
    private final Class<?> menuClass;
    private final RecipeType<?> recipeType;
    private final int[] inputSlotIndices;

    public MachineTransferProfile(String id, Class<?> menuClass, RecipeType<?> recipeType, int... inputSlotIndices) {
        this.id = id;
        this.menuClass = menuClass;
        this.recipeType = recipeType;
        this.inputSlotIndices = inputSlotIndices.clone();
    }

    public String id() {
        return id;
    }

    public Class<?> menuClass() {
        return menuClass;
    }

    public RecipeType<?> recipeType() {
        return recipeType;
    }

    public int inputSlotCount() {
        return inputSlotIndices.length;
    }

    public int inputSlotIndex(int recipeInputIndex) {
        return inputSlotIndices[recipeInputIndex];
    }

    public int[] inputSlotIndices() {
        return inputSlotIndices.clone();
    }

    public boolean matches(AbstractContainerMenu menu) {
        return menuClass.isInstance(menu);
    }

    @Override
    public String toString() {
        return "MachineTransferProfile{" +
                "id='" + id + '\'' +
                ", menuClass=" + menuClass.getSimpleName() +
                ", inputSlotIndices=" + Arrays.toString(inputSlotIndices) +
                '}';
    }
}
