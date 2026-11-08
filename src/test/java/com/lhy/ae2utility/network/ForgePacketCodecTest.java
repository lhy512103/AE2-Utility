package com.lhy.ae2utility.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import com.lhy.ae2utility.network.codec.ByteBufCodecs;

class ForgePacketCodecTest {
    @Test
    void preservesLargeAlternativeListsAndUploadFlags() {
        var packet = new EncodePatternPacket(List.of(Collections.nCopies(71, null)), List.of(), null,
                "pattern", "provider", "display", true, true, false, true, true, false, 17);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            EncodePatternPacket.STREAM_CODEC.encode(buffer, packet);
            var decoded = EncodePatternPacket.STREAM_CODEC.decode(buffer);
            assertEquals(packet, decoded);
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void roundTripsCompositeProviderAcknowledgement() {
        var packet = new InventoryProviderSelectionPreparedPacket(37, true);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            InventoryProviderSelectionPreparedPacket.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, InventoryProviderSelectionPreparedPacket.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsUnboundedListAllocation() {
        var codec = ByteBufCodecs.INT.apply(ByteBufCodecs.list());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> codec.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
