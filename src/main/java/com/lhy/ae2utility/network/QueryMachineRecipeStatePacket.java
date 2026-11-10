package com.lhy.ae2utility.network;

import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import com.lhy.ae2utility.network.codec.StreamCodec;
import com.lhy.ae2utility.network.codec.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;

public record QueryMachineRecipeStatePacket(long requestNonce, int containerId, String profileId, List<RequestedIngredient> requestedIngredients)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<QueryMachineRecipeStatePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "query_machine_recipe_state"));

    public static final StreamCodec<FriendlyByteBuf, QueryMachineRecipeStatePacket> STREAM_CODEC =
            StreamCodec.ofMember(QueryMachineRecipeStatePacket::write, QueryMachineRecipeStatePacket::decode);

    public QueryMachineRecipeStatePacket(int containerId, String profileId, List<RequestedIngredient> requestedIngredients) {
        this(0, containerId, profileId, requestedIngredients);
    }

    public QueryMachineRecipeStatePacket(long requestNonce, int containerId, String profileId, List<RequestedIngredient> requestedIngredients) {
        this.requestNonce = requestNonce;
        this.containerId = containerId;
        this.profileId = profileId;
        this.requestedIngredients = requestedIngredients.stream().map(RequestedIngredient::copy).toList();
    }

    private static QueryMachineRecipeStatePacket decode(FriendlyByteBuf buffer) {
        long nonce = buffer.readLong();
        int containerId = buffer.readVarInt();
        String profileId = buffer.readUtf();
        return new QueryMachineRecipeStatePacket(nonce, containerId, profileId, RequestedIngredientsCodec.readRequestedIngredients(buffer));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeLong(requestNonce);
        buffer.writeVarInt(containerId);
        buffer.writeUtf(profileId);
        RequestedIngredientsCodec.writeRequestedIngredients(buffer, requestedIngredients);
    }

    @Override
    public Type<QueryMachineRecipeStatePacket> type() {
        return TYPE;
    }
}
