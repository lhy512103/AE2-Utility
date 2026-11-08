package com.lhy.ae2utility.network;

import com.lhy.ae2utility.Ae2UtilityMod;

import net.minecraft.network.FriendlyByteBuf;
import com.lhy.ae2utility.network.codec.StreamCodec;
import com.lhy.ae2utility.network.codec.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UploadInventoryPatternToProviderPacket(int playerSlotIndex, long providerId) implements CustomPacketPayload {
    public static final Type<UploadInventoryPatternToProviderPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "upload_inventory_pattern_to_provider"));

    public static final StreamCodec<FriendlyByteBuf, UploadInventoryPatternToProviderPacket> STREAM_CODEC =
            StreamCodec.ofMember(UploadInventoryPatternToProviderPacket::write, UploadInventoryPatternToProviderPacket::decode);

    private static UploadInventoryPatternToProviderPacket decode(FriendlyByteBuf buffer) {
        return new UploadInventoryPatternToProviderPacket(buffer.readVarInt(), buffer.readLong());
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(playerSlotIndex);
        buffer.writeLong(providerId);
    }

    @Override
    public Type<UploadInventoryPatternToProviderPacket> type() {
        return TYPE;
    }
}
