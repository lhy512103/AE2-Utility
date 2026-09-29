package com.lhy.ae2utility.network;

import net.minecraft.network.FriendlyByteBuf;

/** Shared defensive limits for client-originated payloads. */
public final class NetworkValidation {
    public static final int MAX_RECIPE_INPUT_SLOTS = 81;
    public static final int MAX_STACKS_PER_SLOT = 512;
    public static final int MAX_TOTAL_PATTERN_STACKS = 4096;
    public static final int MAX_CRAFTABLE_KEYS = 2048;
    // Vanilla rejects serverbound custom payloads above 32767 bytes; leave room for the channel header.
    public static final int MAX_SERVERBOUND_PAYLOAD_BYTES = 32000;

    private NetworkValidation() {
    }

    public static int readListSize(FriendlyByteBuf buffer, int max, String field) {
        int size = buffer.readVarInt();
        if (size < 0 || size > max) {
            throw new IllegalArgumentException("Invalid " + field + " size " + size + ", max " + max);
        }
        return size;
    }
}
