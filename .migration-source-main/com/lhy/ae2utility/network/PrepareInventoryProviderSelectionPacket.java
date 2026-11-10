package com.lhy.ae2utility.network;

import com.lhy.ae2utility.Ae2UtilityMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Prepares EAEP's pending network-upload context before requesting its provider list.
 * A negative slot clears a previously prepared context.
 */
public record PrepareInventoryProviderSelectionPacket(int playerSlotIndex) implements CustomPacketPayload {
    public static final Type<PrepareInventoryProviderSelectionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "prepare_inventory_provider_selection"));

    public static final StreamCodec<ByteBuf, PrepareInventoryProviderSelectionPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            PrepareInventoryProviderSelectionPacket::playerSlotIndex,
            PrepareInventoryProviderSelectionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}