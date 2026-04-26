package com.lhy.ae2utility.network;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.client.RecipeTreeUploadQueue;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RecipeTreeUploadResultPacket(String patternName, boolean uploaded) implements CustomPacketPayload {
    public static final Type<RecipeTreeUploadResultPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "recipe_tree_upload_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeTreeUploadResultPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUtf(packet.patternName, 256);
                buf.writeBoolean(packet.uploaded);
            },
            buf -> new RecipeTreeUploadResultPacket(buf.readUtf(256), buf.readBoolean()));

    @Override
    public Type<RecipeTreeUploadResultPacket> type() {
        return TYPE;
    }

    public static void handle(RecipeTreeUploadResultPacket packet) {
        RecipeTreeUploadQueue.handleResult(packet);
    }
}
