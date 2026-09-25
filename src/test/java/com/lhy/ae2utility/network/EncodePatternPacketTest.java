package com.lhy.ae2utility.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import appeng.api.stacks.GenericStack;
import net.minecraft.resources.ResourceLocation;

class EncodePatternPacketTest {
    @Test
    void acceptsSeventyOneAlternativesInOneSlot() {
        List<GenericStack> alternatives = Collections.nCopies(71, null);

        assertTrue(EncodePatternPacket.canEncodeStacks(List.of(alternatives), List.of()));
    }

    @Test
    void rejectsOversizedSlotBeforeWritingPacket() {
        List<GenericStack> alternatives = Collections.nCopies(
                NetworkValidation.MAX_STACKS_PER_SLOT + 1, null);

        assertFalse(EncodePatternPacket.canEncodeStacks(List.of(alternatives), List.of()));
    }

    @Test
    void rejectsOversizedAggregateBeforeWritingPacket() {
        List<GenericStack> alternatives = Collections.nCopies(
                NetworkValidation.MAX_STACKS_PER_SLOT, null);
        List<List<GenericStack>> inputs = Collections.nCopies(9, alternatives);

        assertFalse(EncodePatternPacket.canEncodeStacks(inputs, List.of()));
    }

    @Test
    void preservesOmniversalIdentityWhenCopyingBatchFlags() {
        EncodePatternPacket packet = new EncodePatternPacket(
                List.of(), List.of(), null, "", "", "", false, false, false, false, false, false, 0)
                .withOmniversalIdentity(ResourceLocation.fromNamespaceAndPath("useless_mod", "alloy"), "fingerprint", "source");

        EncodePatternPacket copied = packet.withSequentialUpload(true).withEncodeFlags(true, true, false, 7);

        assertTrue(copied.hasOmniversalIdentity());
        assertEquals("fingerprint", copied.omniversalFingerprint());
        assertEquals("source", copied.omniversalSourceId());
        assertEquals(ResourceLocation.fromNamespaceAndPath("useless_mod", "alloy"), copied.omniversalRecipeId());
        assertTrue(copied.shiftDown());
        assertTrue(copied.jeiSequentialQueue());
        assertEquals(7, copied.bulkEncodeSessionId());
    }
}