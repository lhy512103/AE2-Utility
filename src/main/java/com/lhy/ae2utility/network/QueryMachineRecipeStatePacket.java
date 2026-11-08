package com.lhy.ae2utility.network;

import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import com.lhy.ae2utility.network.codec.StreamCodec;
import com.lhy.ae2utility.network.codec.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;

public record QueryMachineRecipeStatePacket(int containerId, String profileId, List<RequestedIngredient> requestedIngredients)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<QueryMachineRecipeStatePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "query_machine_recipe_state"));

    public static final StreamCodec<FriendlyByteBuf, QueryMachineRecipeStatePacket> STREAM_CODEC =
            StreamCodec.ofMember(QueryMachineRecipeStatePacket::write, QueryMachineRecipeStatePacket::decode);

    public QueryMachineRecipeStatePacket(int containerId, String profileId, List<RequestedIngredient> requestedIngredients) {
        this.containerId = containerId;
        this.profileId = profileId;
        this.requestedIngredients = requestedIngredients.stream().map(RequestedIngredient::copy).toList();
    }

    private static QueryMachineRecipeStatePacket decode(FriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        String profileId = buffer.readUtf();
        return new QueryMachineRecipeStatePacket(containerId, profileId, RecipeTransferPacketHelper.readRequestedIngredients(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeUtf(profileId);
        RecipeTransferPacketHelper.writeRequestedIngredients(buffer, requestedIngredients);
    }

    @Override
    public Type<QueryMachineRecipeStatePacket> type() {
        return TYPE;
    }
}
