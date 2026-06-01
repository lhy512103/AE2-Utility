package com.lhy.ae2utility.client;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;

import com.lhy.ae2utility.network.EncodePatternPacket;

public final class RecipeTreeUploadProgressState {
    private static @Nullable String currentPatternName;
    private static @Nullable String currentMachineName;
    private static @Nullable ResourceLocation currentRecipeId;

    private RecipeTreeUploadProgressState() {
    }

    public static void setCurrent(EncodePatternPacket packet) {
        currentRecipeId = packet.recipeId();
        String name = packet.patternName();
        if (name == null || name.isBlank()) {
            name = packet.recipeId() != null ? packet.recipeId().toString() : "-";
        }
        setCurrent(name,
                packet.providerDisplayName() != null && !packet.providerDisplayName().isBlank()
                        ? packet.providerDisplayName()
                        : packet.providerSearchKey());
    }

    public static void setCurrent(@Nullable String patternName, @Nullable String machineName) {
        currentRecipeId = null;
        currentPatternName = patternName == null || patternName.isBlank() ? "-" : patternName;
        currentMachineName = localizeMachineName(machineName);
    }

    public static @Nullable ResourceLocation currentRecipeId() {
        return currentRecipeId;
    }

    public static @Nullable String currentPatternName() {
        return currentPatternName;
    }

    public static @Nullable String currentMachineName() {
        return currentMachineName;
    }

    public static void clear() {
        currentPatternName = null;
        currentMachineName = null;
        currentRecipeId = null;
    }

    private static String localizeMachineName(@Nullable String machineName) {
        if (machineName == null || machineName.isBlank()) {
            return "-";
        }
        String resolved = com.lhy.ae2utility.integration.eaep.EaepReflection.resolveSearchKeyAlias(machineName);
        return resolved != null && !resolved.isBlank() ? resolved : machineName;
    }
}
