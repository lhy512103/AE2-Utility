package com.lhy.ae2utility.network;

import java.util.ArrayList;
import java.util.List;

import com.lhy.ae2utility.Ae2UtilityMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
