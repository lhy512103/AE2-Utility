package com.lhy.ae2utility.network;

import java.util.List;

import com.lhy.ae2utility.Ae2UtilityMod;

import io.netty.buffer.ByteBuf;
import com.lhy.ae2utility.network.codec.ByteBufCodecs;
import com.lhy.ae2utility.network.codec.StreamCodec;
import com.lhy.ae2utility.network.codec.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FallbackToProviderSelectionPacket(List<Integer> slots) implements CustomPacketPayload {
    public static final Type<FallbackToProviderSelectionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "fallback_to_provider_selection"));

    public static final StreamCodec<ByteBuf, FallbackToProviderSelectionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT.apply(ByteBufCodecs.list()),
            FallbackToProviderSelectionPacket::slots,
            FallbackToProviderSelectionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
