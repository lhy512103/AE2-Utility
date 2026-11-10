package com.lhy.ae2utility.card;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class RedstoneSignalCardModeTest {
    @Test
    void codecRoundTripsAllModes() {
        for (RedstoneSignalCardMode mode : RedstoneSignalCardMode.values()) {
            var encoded = RedstoneSignalCardMode.CODEC.encodeStart(JsonOps.INSTANCE, mode).getOrThrow();
            var decoded = RedstoneSignalCardMode.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
            assertEquals(mode, decoded);
        }
    }

    @Test
    void codecRejectsUnknownMode() {
        var result = RedstoneSignalCardMode.CODEC.parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createString("invalid"));
        assertTrue(result.isError());
    }
}
