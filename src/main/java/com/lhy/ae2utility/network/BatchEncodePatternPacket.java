package com.lhy.ae2utility.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.lhy.ae2utility.Ae2UtilityMod;

public record BatchEncodePatternPacket(List<EncodePatternPacket> patterns) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BatchEncodePatternPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "batch_encode_patterns"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BatchEncodePatternPacket> STREAM_CODEC =
            StreamCodec.ofMember(BatchEncodePatternPacket::write, BatchEncodePatternPacket::decode);

    public BatchEncodePatternPacket {
        patterns = Collections.unmodifiableList(new ArrayList<>(patterns));
    }

    private static BatchEncodePatternPacket decode(RegistryFriendlyByteBuf buffer) {
        int n = buffer.readVarInt();
        List<EncodePatternPacket> list = new ArrayList<>(Math.min(n, 4096));
        for (int i = 0; i < n; i++) {
            list.add(EncodePatternPacket.STREAM_CODEC.decode(buffer));
        }
        return new BatchEncodePatternPacket(list);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(patterns.size());
        for (EncodePatternPacket p : patterns) {
            EncodePatternPacket.STREAM_CODEC.encode(buffer, p);
        }
    }

    @Override
    public Type<BatchEncodePatternPacket> type() {
        return TYPE;
    }
}
