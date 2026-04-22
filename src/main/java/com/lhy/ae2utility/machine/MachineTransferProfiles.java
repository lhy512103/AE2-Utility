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
import appeng.recipes.AERecipeTypes;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;

public final class MachineTransferProfiles {
    public static final MachineTransferProfile FURNACE = new MachineTransferProfile(
            "furnace", FurnaceMenu.class, RecipeTypes.SMELTING, 0);
    public static final MachineTransferProfile SMOKER = new MachineTransferProfile(
            "smoker", SmokerMenu.class, RecipeTypes.SMOKING, 0);
    public static final MachineTransferProfile BLAST_FURNACE = new MachineTransferProfile(
            "blast_furnace", BlastFurnaceMenu.class, RecipeTypes.BLASTING, 0);
    public static final MachineTransferProfile STONECUTTER = new MachineTransferProfile(
            "stonecutter", StonecutterMenu.class, RecipeTypes.STONECUTTING, 0);
    public static final MachineTransferProfile SMITHING = new MachineTransferProfile(
            "smithing", SmithingMenu.class, RecipeTypes.SMITHING, 0, 1, 2);
    public static final MachineTransferProfile INSCRIBER = new MachineTransferProfile(
            "inscriber", InscriberMenu.class, RecipeType.createFromVanilla(AERecipeTypes.INSCRIBER), 40, 42, 41);

    private static final List<MachineTransferProfile> ALL = buildAll();
    private static final Map<String, MachineTransferProfile> BY_ID = createById();

    private MachineTransferProfiles() {
    }

    private static List<MachineTransferProfile> buildAll() {
        List<MachineTransferProfile> list = new ArrayList<>(List.of(
                FURNACE,
                SMOKER,
                BLAST_FURNACE,
                STONECUTTER,
                SMITHING,
                INSCRIBER));

        // AdvancedAE Reaction Chamber (9 inputs)
        addExternal(list, "reaction_chamber", "net.pedroksl.advanced_ae.gui.ReactionChamberMenu", "advanced_ae", "reaction_chamber", "net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe", 40, 41, 42, 43, 44, 45, 46, 47, 48);

        // ExtendedAE Crystal Assembler (9 inputs)
        addExternalVanilla(list, "crystal_assembler", "com.glodblock.github.extendedae.container.ContainerCrystalAssembler", "extendedae", "crystal_assembler", 40, 41, 42, 43, 44, 45, 46, 47, 48);

        // ExtendedAE Circuit Cutter (1 input)
        addExternalVanilla(list, "circuit_cutter", "com.glodblock.github.extendedae.container.ContainerCircuitCutter", "extendedae", "circuit_cutter", 40);

        // ExtendedAE Ex Inscriber (4 threads, we bind the first thread inputs: top, middle, bottom)
        addExternal(list, "ex_inscriber", "com.glodblock.github.extendedae.container.ContainerExInscriber", RecipeType.createFromVanilla(AERecipeTypes.INSCRIBER), 40, 42, 41);

        return List.copyOf(list);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addExternal(List<MachineTransferProfile> list, String id, String menuClassName, String recipeNamespace, String recipePath, String recipeClassName, int... inputSlotIndices) {
        try {
            Class<?> menuClass = Class.forName(menuClassName);
            Class<?> recipeClass = Class.forName(recipeClassName);
            list.add(new MachineTransferProfile(id, menuClass, RecipeType.create(recipeNamespace, recipePath, (Class) recipeClass), inputSlotIndices));
        } catch (Exception | NoClassDefFoundError ignored) {
        }
    }

    private static void addExternal(List<MachineTransferProfile> list, String id, String menuClassName, RecipeType<?> recipeType, int... inputSlotIndices) {
        try {
            Class<?> menuClass = Class.forName(menuClassName);
            list.add(new MachineTransferProfile(id, menuClass, recipeType, inputSlotIndices));
        } catch (Exception | NoClassDefFoundError ignored) {
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addExternalVanilla(List<MachineTransferProfile> list, String id, String menuClassName, String recipeNamespace, String recipePath, int... inputSlotIndices) {
        try {
            Class<?> menuClass = Class.forName(menuClassName);
            net.minecraft.world.item.crafting.RecipeType<?> vanillaType = net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(recipeNamespace, recipePath));
            if (vanillaType != null) {
                list.add(new MachineTransferProfile(id, menuClass, RecipeType.createFromVanilla((net.minecraft.world.item.crafting.RecipeType) vanillaType), inputSlotIndices));
            }
        } catch (Exception | NoClassDefFoundError ignored) {
        }
    }

    public static List<MachineTransferProfile> all() {
        return ALL;
    }

    @Nullable
    public static MachineTransferProfile byId(String id) {
        return BY_ID.get(id);
    }

    @Nullable
    public static MachineTransferProfile forMenu(AbstractContainerMenu menu) {
        for (MachineTransferProfile profile : ALL) {
            if (profile.matches(menu)) {
                return profile;
            }
        }
        return null;
    }

    private static Map<String, MachineTransferProfile> createById() {
        Map<String, MachineTransferProfile> profiles = new LinkedHashMap<>();
        for (MachineTransferProfile profile : ALL) {
            profiles.put(profile.id(), profile);
        }
        return profiles;
    }
}
