package com.lhy.ae2utility.machine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.StonecutterMenu;

import appeng.menu.implementations.InscriberMenu;

/** Common machine menu/slot metadata; must not depend on a recipe viewer. */
public final class MachineTransferProfiles {
    public static final MachineTransferProfile FURNACE = new MachineTransferProfile(
            "furnace", FurnaceMenu.class, 0);
    public static final MachineTransferProfile SMOKER = new MachineTransferProfile(
            "smoker", SmokerMenu.class, 0);
    public static final MachineTransferProfile BLAST_FURNACE = new MachineTransferProfile(
            "blast_furnace", BlastFurnaceMenu.class, 0);
    public static final MachineTransferProfile STONECUTTER = new MachineTransferProfile(
            "stonecutter", StonecutterMenu.class, 0);
    public static final MachineTransferProfile SMITHING = new MachineTransferProfile(
            "smithing", SmithingMenu.class, 0, 1, 2);
    public static final MachineTransferProfile INSCRIBER = new MachineTransferProfile(
            "inscriber", InscriberMenu.class, 40, 42, 41);

    private static final Map<String, MachineTransferProfile> PROFILES = createProfiles();

    private MachineTransferProfiles() {
    }

    private static Map<String, MachineTransferProfile> createProfiles() {
        Map<String, MachineTransferProfile> profiles = new LinkedHashMap<>();
        for (MachineTransferProfile profile : buildAll()) {
            profiles.put(profile.id(), profile);
        }
        return profiles;
    }

    private static List<MachineTransferProfile> buildAll() {
        List<MachineTransferProfile> list = new ArrayList<>(List.of(
                FURNACE, SMOKER, BLAST_FURNACE, STONECUTTER, SMITHING, INSCRIBER));

        // Optional menus are resolved without initializing their client integrations.
        addExternal(list, "reaction_chamber", "net.pedroksl.advanced_ae.gui.ReactionChamberMenu", 40, 41, 42, 43, 44, 45, 46, 47, 48);
        addExternal(list, "crystal_assembler", "com.glodblock.github.extendedae.container.ContainerCrystalAssembler", 40, 41, 42, 43, 44, 45, 46, 47, 48);
        addExternal(list, "circuit_cutter", "com.glodblock.github.extendedae.container.ContainerCircuitCutter", 40);
        // First Ex Inscriber thread: top, middle, bottom.
        addExternal(list, "ex_inscriber", "com.glodblock.github.extendedae.container.ContainerExInscriber", 40, 42, 41);
        return List.copyOf(list);
    }

    private static void addExternal(List<MachineTransferProfile> list, String id, String menuClassName, int... inputSlotIndices) {
        try {
            Class<?> menuClass = Class.forName(menuClassName, false, MachineTransferProfiles.class.getClassLoader());
            list.add(new MachineTransferProfile(id, menuClass, inputSlotIndices));
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
        }
    }

    public static synchronized List<MachineTransferProfile> all() {
        return List.copyOf(PROFILES.values());
    }

    public static synchronized void register(MachineTransferProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("profile is required");
        }
        if (PROFILES.containsKey(profile.id())) {
            throw new IllegalArgumentException("machine transfer profile already registered: " + profile.id());
        }
        PROFILES.put(profile.id(), profile);
    }

    @Nullable
    public static synchronized MachineTransferProfile byId(String id) {
        return PROFILES.get(id);
    }

    @Nullable
    public static synchronized String resolveId(net.minecraft.resources.ResourceLocation id) {
        String namespaced = id.toString();
        if (PROFILES.containsKey(namespaced)) {
            return namespaced;
        }
        if ("ae2utility".equals(id.getNamespace()) && PROFILES.containsKey(id.getPath())) {
            return id.getPath();
        }
        return null;
    }

    @Nullable
    public static synchronized MachineTransferProfile forMenu(AbstractContainerMenu menu) {
        for (MachineTransferProfile profile : PROFILES.values()) {
            if (profile.matches(menu)) {
                return profile;
            }
        }
        return null;
    }
}
