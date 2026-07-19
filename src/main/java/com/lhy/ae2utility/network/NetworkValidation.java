package com.lhy.ae2utility.network;

import net.minecraft.network.RegistryFriendlyByteBuf;

/** Shared defensive limits for client-originated payloads. */
public final class NetworkValidation {
    public static final int MAX_REQUESTED_INGREDIENTS = 128;
    public static final int MAX_ALTERNATIVES_PER_INGREDIENT = 64;
    public static final int MAX_PATTERN_BATCH = 256;
    public static final int MAX_INVENTORY_SLOTS = 128;
    public static final int MAX_RECIPE_INPUT_SLOTS = 64;
    public static final int MAX_STACKS_PER_SLOT = 64;
    public static final int MAX_CRAFTABLE_KEYS = 2048;

    private NetworkValidation() {
    }

    public static int readListSize(RegistryFriendlyByteBuf buffer, int max, String field) {
        int size = buffer.readVarInt();
        if (size < 0 || size > max) {
            throw new IllegalArgumentException("Invalid " + field + " size " + size + ", max " + max);
        }
        return size;
    }

    public static int requireSize(int size, int max, String field) {
        if (size < 0 || size > max) {
            throw new IllegalArgumentException("Invalid " + field + " size " + size + ", max " + max);
        }
        return size;
    }
}
