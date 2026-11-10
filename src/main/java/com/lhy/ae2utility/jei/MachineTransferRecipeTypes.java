package com.lhy.ae2utility.jei;

import java.util.HashMap;
import java.util.Map;

import com.lhy.ae2utility.machine.MachineTransferProfile;
import com.lhy.ae2utility.machine.MachineTransferProfiles;

import appeng.recipes.handlers.InscriberRecipe;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;

/** JEI-only recipe associations, never loaded by common machine profile lookups. */
public final class MachineTransferRecipeTypes {
    private static final Map<String, RecipeType<?>> CUSTOM_TYPES = new HashMap<>();

    private MachineTransferRecipeTypes() {
    }

    public static synchronized void register(net.minecraft.resources.ResourceLocation profileId, Object recipeType) {
        if (!(recipeType instanceof RecipeType<?> type)) {
            throw new IllegalArgumentException("recipeType must be a JEI recipe type");
        }
        if (MachineTransferProfiles.resolveId(profileId) == null) {
            throw new IllegalArgumentException("machine transfer profile is not registered: " + profileId);
        }
        CUSTOM_TYPES.put(MachineTransferProfiles.resolveId(profileId), type);
    }

    public static synchronized RecipeType<?> forProfile(MachineTransferProfile profile) {
        RecipeType<?> customType = CUSTOM_TYPES.get(profile.id());
        if (customType != null) {
            return customType;
        }
        return switch (profile.id()) {
            case "furnace" -> RecipeTypes.SMELTING;
            case "smoker" -> RecipeTypes.SMOKING;
            case "blast_furnace" -> RecipeTypes.BLASTING;
            case "stonecutter" -> RecipeTypes.STONECUTTING;
            case "smithing" -> RecipeTypes.SMITHING;
            case "inscriber", "ex_inscriber" -> RecipeType.create("ae2", "inscriber", InscriberRecipe.class);
            case "reaction_chamber" -> external("advanced_ae", "reaction_chamber",
                    "net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe");
            case "crystal_assembler" -> external("extendedae", "crystal_assembler",
                    "com.glodblock.github.extendedae.recipe.CrystalAssemblerRecipe");
            case "circuit_cutter" -> external("expatternprovider", "circuit_cutter",
                    "com.glodblock.github.extendedae.recipe.CircuitCutterRecipe");
            default -> null;
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RecipeType<?> external(String namespace, String path, String recipeClassName) {
        try {
            return RecipeType.create(namespace, path, (Class) Class.forName(recipeClassName));
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            return null;
        }
    }
}
