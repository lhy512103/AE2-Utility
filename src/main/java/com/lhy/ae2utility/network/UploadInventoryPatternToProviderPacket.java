package com.lhy.ae2utility.network;

import com.lhy.ae2utility.Ae2UtilityMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UploadInventoryPatternToProviderPacket(int playerSlotIndex, long providerId) implements CustomPacketPayload {
    public static final Type<UploadInventoryPatternToProviderPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "upload_inventory_pattern_to_provider"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UploadInventoryPatternToProviderPacket> STREAM_CODEC =
            StreamCodec.ofMember(UploadInventoryPatternToProviderPacket::write, UploadInventoryPatternToProviderPacket::decode);

    private static UploadInventoryPatternToProviderPacket decode(RegistryFriendlyByteBuf buffer) {
        return new UploadInventoryPatternToProviderPacket(buffer.readVarInt(), buffer.readLong());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(playerSlotIndex);
        buffer.writeLong(providerId);
    }

    @Override
    public Type<UploadInventoryPatternToProviderPacket> type() {
        return TYPE;
    }
}
