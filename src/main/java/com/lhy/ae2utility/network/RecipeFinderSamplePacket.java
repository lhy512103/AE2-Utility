package com.lhy.ae2utility.network;

import net.minecraft.network.FriendlyByteBuf;
import com.lhy.ae2utility.network.codec.StreamCodec;
import com.lhy.ae2utility.network.codec.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.lhy.ae2utility.Ae2UtilityMod;

public record RecipeFinderSamplePacket(ItemStack stack) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RecipeFinderSamplePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "recipe_finder_sample"));

    public static final StreamCodec<FriendlyByteBuf, RecipeFinderSamplePacket> STREAM_CODEC = StreamCodec.composite(
            com.lhy.ae2utility.network.codec.ForgeCodecs.ITEM_STACK,
            RecipeFinderSamplePacket::stack,
            RecipeFinderSamplePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
