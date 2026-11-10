package com.lhy.ae2utility.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.lhy.ae2utility.Ae2UtilityMod;

public record RecipeFinderSamplePacket(ItemStack stack) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RecipeFinderSamplePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "recipe_finder_sample"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeFinderSamplePacket> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC,
            RecipeFinderSamplePacket::stack,
            RecipeFinderSamplePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
