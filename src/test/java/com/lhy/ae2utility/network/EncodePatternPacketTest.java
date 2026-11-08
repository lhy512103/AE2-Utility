package com.lhy.ae2utility.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import appeng.api.stacks.GenericStack;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
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
    void rejectsAggregateInputOverflowBeforeReadingOffendingSlotContents() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writeRawHeader(buffer, 9);
            for (int i = 0; i < 8; i++) {
                buffer.writeBoolean(true);
                writeNullStacks(buffer, NetworkValidation.MAX_STACKS_PER_SLOT);
            }
            buffer.writeBoolean(true);
            buffer.writeVarInt(1);
            int contentsStart = buffer.writerIndex();
            buffer.writeBoolean(false);

            assertThrows(IllegalArgumentException.class, () -> EncodePatternPacket.STREAM_CODEC.decode(buffer));
            assertEquals(contentsStart, buffer.readerIndex(), "must reject before consuming the ninth slot's contents");
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsCombinedInputOutputOverflowBeforeReadingOutputContents() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writeRawHeader(buffer, 8);
            for (int i = 0; i < 8; i++) {
                buffer.writeBoolean(true);
                writeNullStacks(buffer, i == 7 ? NetworkValidation.MAX_STACKS_PER_SLOT - 1
                        : NetworkValidation.MAX_STACKS_PER_SLOT);
            }
            // Inputs use 4095 entries; both output entries fit the per-list limit, not the shared budget.
            buffer.writeVarInt(2);
            int contentsStart = buffer.writerIndex();
            buffer.writeBoolean(false);
            buffer.writeBoolean(false);

            assertThrows(IllegalArgumentException.class, () -> EncodePatternPacket.STREAM_CODEC.decode(buffer));
            assertEquals(contentsStart, buffer.readerIndex(), "must reject before consuming output contents");
        } finally {
            buffer.release();
        }
    }

    @Test
    void decodesRawPacketAtExactCombinedStackBudget() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writeRawHeader(buffer, 7);
            for (int i = 0; i < 7; i++) {
                buffer.writeBoolean(true);
                writeNullStacks(buffer, NetworkValidation.MAX_STACKS_PER_SLOT);
            }
            writeNullStacks(buffer, NetworkValidation.MAX_STACKS_PER_SLOT);

            EncodePatternPacket decoded = EncodePatternPacket.STREAM_CODEC.decode(buffer);
            int total = decoded.inputs().stream().mapToInt(List::size).sum() + decoded.outputs().size();
            assertEquals(NetworkValidation.MAX_TOTAL_PATTERN_STACKS, total);
            assertEquals(NetworkValidation.MAX_STACKS_PER_SLOT, decoded.outputs().size());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void roundTripsExactCombinedStackBudget() {
        var packet = new EncodePatternPacket(
                Collections.nCopies(7, Collections.nCopies(NetworkValidation.MAX_STACKS_PER_SLOT, null)),
                Collections.nCopies(NetworkValidation.MAX_STACKS_PER_SLOT, null), null,
                "pattern", "provider", "display", true, true, false, true, true, false, 17);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            EncodePatternPacket.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, EncodePatternPacket.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    private static void writeRawHeader(FriendlyByteBuf buffer, int inputSlots) {
        buffer.writeBoolean(false);
        buffer.writeUtf("", 256);
        buffer.writeUtf("", 256);
        buffer.writeUtf("", 256);
        for (int i = 0; i < 4; i++) buffer.writeBoolean(false);
        buffer.writeVarInt(inputSlots);
    }

    private static void writeNullStacks(FriendlyByteBuf buffer, int size) {
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) buffer.writeBoolean(false);
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