package com.lhy.ae2utility.network;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;

public record PullMachineRecipeInputsPacket(int containerId, String profileId, boolean maxTransfer,
        List<RequestedIngredient> requestedIngredients) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PullMachineRecipeInputsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "pull_machine_recipe_inputs"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PullMachineRecipeInputsPacket> STREAM_CODEC =
            StreamCodec.ofMember(PullMachineRecipeInputsPacket::write, PullMachineRecipeInputsPacket::decode);

    public PullMachineRecipeInputsPacket(int containerId, String profileId, boolean maxTransfer,
            List<RequestedIngredient> requestedIngredients) {
        this.containerId = containerId;
        this.profileId = profileId;
        this.maxTransfer = maxTransfer;
        this.requestedIngredients = requestedIngredients.stream().map(RequestedIngredient::copy).toList();
    }

    private static PullMachineRecipeInputsPacket decode(RegistryFriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        String profileId = buffer.readUtf();
        boolean maxTransfer = buffer.readBoolean();
        return new PullMachineRecipeInputsPacket(containerId, profileId, maxTransfer,
                RecipeTransferPacketHelper.readRequestedIngredients(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeUtf(profileId);
        buffer.writeBoolean(maxTransfer);
        RecipeTransferPacketHelper.writeRequestedIngredients(buffer, requestedIngredients);
    }

    @Override
    public Type<PullMachineRecipeInputsPacket> type() {
        return TYPE;
    }
}
