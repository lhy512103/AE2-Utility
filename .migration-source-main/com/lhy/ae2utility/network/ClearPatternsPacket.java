package com.lhy.ae2utility.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.lhy.ae2utility.Ae2UtilityMod;

public class ClearPatternsPacket implements CustomPacketPayload {
    public static final Type<ClearPatternsPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "clear_patterns"));
    
    public static final ClearPatternsPacket INSTANCE = new ClearPatternsPacket();
    
    public static final StreamCodec<FriendlyByteBuf, ClearPatternsPacket> STREAM_CODEC = StreamCodec.of(
        (buf, pkt) -> {},
        buf -> INSTANCE
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
