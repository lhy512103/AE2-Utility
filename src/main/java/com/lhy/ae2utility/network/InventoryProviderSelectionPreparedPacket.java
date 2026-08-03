package com.lhy.ae2utility.network;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.client.InventoryPatternUploadQueue;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record InventoryProviderSelectionPreparedPacket(int playerSlotIndex, boolean success) implements CustomPacketPayload {
    public static final Type<InventoryProviderSelectionPreparedPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "inventory_provider_selection_prepared"));

    public static final StreamCodec<ByteBuf, InventoryProviderSelectionPreparedPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            InventoryProviderSelectionPreparedPacket::playerSlotIndex,
            ByteBufCodecs.BOOL,
            InventoryProviderSelectionPreparedPacket::success,
            InventoryProviderSelectionPreparedPacket::new);

    public static void handle(InventoryProviderSelectionPreparedPacket packet) {
        InventoryPatternUploadQueue.handleProviderSelectionPrepared(packet.playerSlotIndex(), packet.success());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}