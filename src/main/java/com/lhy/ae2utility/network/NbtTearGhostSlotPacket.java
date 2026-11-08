package com.lhy.ae2utility.network;

import net.minecraft.network.FriendlyByteBuf;
import com.lhy.ae2utility.network.codec.ByteBufCodecs;
import com.lhy.ae2utility.network.codec.StreamCodec;
import com.lhy.ae2utility.network.codec.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.lhy.ae2utility.Ae2UtilityMod;

public record NbtTearGhostSlotPacket(int slotIndex, ItemStack stack) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<NbtTearGhostSlotPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "nbt_tear_ghost_slot"));

    public static final StreamCodec<FriendlyByteBuf, NbtTearGhostSlotPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            NbtTearGhostSlotPacket::slotIndex,
            com.lhy.ae2utility.network.codec.ForgeCodecs.ITEM_STACK,
            NbtTearGhostSlotPacket::stack,
            NbtTearGhostSlotPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
