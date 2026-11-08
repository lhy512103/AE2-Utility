package com.lhy.ae2utility.machine;

import java.util.Arrays;

import net.minecraft.world.inventory.AbstractContainerMenu;

public final class MachineTransferProfile {
    private final String id;
    private final Class<?> menuClass;
    private final int[] inputSlotIndices;

    public MachineTransferProfile(String id, Class<?> menuClass, int... inputSlotIndices) {
        this.id = id;
        this.menuClass = menuClass;
        this.inputSlotIndices = inputSlotIndices.clone();
    }

    public String id() {
        return id;
    }

    public Class<?> menuClass() {
        return menuClass;
    }

    public int inputSlotCount() {
        return inputSlotIndices.length;
    }

    public int inputSlotIndex(int recipeInputIndex) {
        return inputSlotIndices[recipeInputIndex];
    }

    /** AE2's network-tool inventory adds slots before the machine's inputs in 1.20.1. */
    public int inputSlotIndex(AbstractContainerMenu menu, int recipeInputIndex) {
        if (menu instanceof appeng.menu.AEBaseMenu aeMenu) {
            appeng.menu.SlotSemantic semantic = null;
            int semanticIndex = recipeInputIndex;
            if (id.equals("inscriber") || id.equals("ex_inscriber")) {
                semantic = switch (recipeInputIndex) {
                    case 0 -> appeng.menu.SlotSemantics.INSCRIBER_PLATE_TOP;
                    case 1 -> appeng.menu.SlotSemantics.MACHINE_INPUT;
                    case 2 -> appeng.menu.SlotSemantics.INSCRIBER_PLATE_BOTTOM;
                    default -> null;
                };
                semanticIndex = 0;
            } else if (id.equals("reaction_chamber") || id.equals("crystal_assembler") || id.equals("circuit_cutter")) {
                semantic = appeng.menu.SlotSemantics.MACHINE_INPUT;
            }
            if (semantic != null) {
                var slots = aeMenu.getSlots(semantic);
                if (semanticIndex < slots.size()) return slots.get(semanticIndex).index;
            }
        }
        return inputSlotIndex(recipeInputIndex);
    }

    public int[] inputSlotIndices(AbstractContainerMenu menu) {
        return java.util.stream.IntStream.range(0, inputSlotCount()).map(i -> inputSlotIndex(menu, i)).toArray();
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
