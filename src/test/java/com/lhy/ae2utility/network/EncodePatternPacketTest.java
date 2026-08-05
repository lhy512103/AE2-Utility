package com.lhy.ae2utility.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import appeng.api.stacks.GenericStack;

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
}