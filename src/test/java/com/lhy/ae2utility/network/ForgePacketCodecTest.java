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
    void queryAndReplyNoncesRoundTrip() {
        var query = new QueryCraftableStatePacket(0x1122334455667788L, List.of());
        var response = new CraftableStatePacket(0x1122334455667788L, List.of(), List.of());
        var machineQuery = new QueryMachineRecipeStatePacket(23L, 7, "profile", List.of());
        var machineResponse = new MachineRecipeStatePacket(23L, 7, "profile", "signature",
                MachineRecipeStatePacket.State.READY, List.of());
        assertRoundTrip(QueryCraftableStatePacket.STREAM_CODEC, query);
        assertRoundTrip(CraftableStatePacket.STREAM_CODEC, response);
        assertRoundTrip(QueryMachineRecipeStatePacket.STREAM_CODEC, machineQuery);
        assertRoundTrip(MachineRecipeStatePacket.STREAM_CODEC, machineResponse);
    }

    private static <T> void assertRoundTrip(com.lhy.ae2utility.network.codec.StreamCodec<FriendlyByteBuf, T> codec,
            T packet) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, packet);
            assertEquals(packet, codec.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

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
