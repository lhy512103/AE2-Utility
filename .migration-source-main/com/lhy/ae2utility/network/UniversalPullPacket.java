package com.lhy.ae2utility.network;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;

public record UniversalPullPacket(List<RequestedIngredient> requestedIngredients, boolean maxTransfer) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UniversalPullPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "universal_pull"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UniversalPullPacket> STREAM_CODEC =
            StreamCodec.ofMember(UniversalPullPacket::write, UniversalPullPacket::decode);

    public UniversalPullPacket(List<RequestedIngredient> requestedIngredients, boolean maxTransfer) {
        this.requestedIngredients = requestedIngredients.stream().map(RequestedIngredient::copy).toList();
        this.maxTransfer = maxTransfer;
    }

    private static UniversalPullPacket decode(RegistryFriendlyByteBuf buffer) {
        return new UniversalPullPacket(
                RecipeTransferPacketHelper.readRequestedIngredients(buffer),
                buffer.readBoolean()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        RecipeTransferPacketHelper.writeRequestedIngredients(buffer, requestedIngredients);
        buffer.writeBoolean(maxTransfer);
    }

    @Override
    public Type<UniversalPullPacket> type() {
        return TYPE;
    }
}
