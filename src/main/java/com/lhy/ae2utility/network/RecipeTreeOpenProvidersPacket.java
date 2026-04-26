package com.lhy.ae2utility.network;

import com.lhy.ae2utility.Ae2UtilityMod;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public record RecipeTreeOpenProvidersPacket() implements CustomPacketPayload {
    public static final Type<RecipeTreeOpenProvidersPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "recipe_tree_open_providers"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeTreeOpenProvidersPacket> STREAM_CODEC =
            StreamCodec.unit(new RecipeTreeOpenProvidersPacket());

    @Override
    public Type<RecipeTreeOpenProvidersPacket> type() {
        return TYPE;
    }

    public static void handle() {
        if (!net.neoforged.fml.ModList.get().isLoaded("extendedae_plus")) {
            return;
        }
        try {
            Class<?> packetClass = Class.forName("com.extendedae_plus.network.RequestProvidersListC2SPacket");
            Object instance = packetClass.getDeclaredField("INSTANCE").get(null);
            if (instance instanceof net.minecraft.network.protocol.common.custom.CustomPacketPayload payload
                    && Minecraft.getInstance().player != null) {
                PacketDistributor.sendToServer(payload);
            }
        } catch (Throwable ignored) {
        }
    }
}
