package com.lhy.ae2utility.api.client;

import java.util.Objects;

import com.lhy.ae2utility.api.transfer.MachineTransferClientAdapter;
import com.lhy.ae2utility.api.transfer.MachineTransferRegistration;
import com.lhy.ae2utility.api.transfer.RecipeTransferApi;
import mezz.jei.api.recipe.RecipeType;

/**
 * JEI-only client API for associating a common machine profile with a recipe type.
 * Load/call this class only from a physical-client integration when JEI is present.
 */
public final class JeiMachineTransferApi {
    private JeiMachineTransferApi() {
    }

    /** Registers the common menu profile and adds its JEI recipe association. */
    public static void registerMachine(MachineTransferRegistration registration, RecipeType<?> recipeType) {
        Objects.requireNonNull(registration, "registration");
        Objects.requireNonNull(recipeType, "recipeType");
        if (!MachineTransferClientAdapter.isInstalled()) {
            throw new IllegalStateException("JEI machine transfers must be registered after client setup");
        }
        RecipeTransferApi.INSTANCE.registerMachine(registration);
        MachineTransferClientAdapter.associate(registration.id(), recipeType);
    }

    /** Associates a recipe type with an already registered common profile. */
    public static void associateRecipeType(MachineTransferRegistration registration, RecipeType<?> recipeType) {
        Objects.requireNonNull(registration, "registration");
        Objects.requireNonNull(recipeType, "recipeType");
        MachineTransferClientAdapter.associate(registration.id(), recipeType);
    }
}
