package com.lhy.ae2utility.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.lhy.ae2utility.Ae2UtilityMod;

public record RecipeFinderEncodePacket(List<EncodePatternPacket> patterns) implements CustomPacketPayload {
    public static final Type<RecipeFinderEncodePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "recipe_finder_encode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeFinderEncodePacket> STREAM_CODEC =
            StreamCodec.ofMember(RecipeFinderEncodePacket::write, RecipeFinderEncodePacket::decode);

    private static RecipeFinderEncodePacket decode(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<EncodePatternPacket> selected = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            selected.add(EncodePatternPacket.STREAM_CODEC.decode(buffer));
        }
        return new RecipeFinderEncodePacket(selected);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(patterns.size());
        for (EncodePatternPacket pattern : patterns) {
            EncodePatternPacket.STREAM_CODEC.encode(buffer, pattern);
        }
    }

    @Override
    public Type<RecipeFinderEncodePacket> type() {
        return TYPE;
    }
}
