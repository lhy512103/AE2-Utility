package com.lhy.ae2utility.api.transfer;

import java.util.Objects;
import java.util.function.BiConsumer;

import net.minecraft.resources.ResourceLocation;

/** Neutral dispatch used by the separately loaded JEI client API. */
public final class MachineTransferClientAdapter {
    private static volatile BiConsumer<ResourceLocation, Object> registrar;

    private MachineTransferClientAdapter() {
    }

    public static void install(BiConsumer<ResourceLocation, Object> adapter) {
        registrar = Objects.requireNonNull(adapter, "adapter");
    }

    public static void associate(ResourceLocation profileId, Object recipeType) {
        BiConsumer<ResourceLocation, Object> adapter = registrar;
        if (adapter == null) {
            throw new IllegalStateException("Machine recipe-viewer registration requires a physical client with JEI");
        }
        adapter.accept(Objects.requireNonNull(profileId, "profileId"), Objects.requireNonNull(recipeType, "recipeType"));
    }

    public static boolean isInstalled() {
        return registrar != null;
    }
}
