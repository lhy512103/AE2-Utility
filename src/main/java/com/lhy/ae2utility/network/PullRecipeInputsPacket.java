package com.lhy.ae2utility.network;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.lhy.ae2utility.Ae2UtilityMod;

public record PullRecipeInputsPacket(boolean maxTransfer, boolean craftMissing,
        List<RequestedIngredient> requestedIngredients)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PullRecipeInputsPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "pull_recipe_inputs"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PullRecipeInputsPacket> STREAM_CODEC =
            StreamCodec.ofMember(PullRecipeInputsPacket::write, PullRecipeInputsPacket::decode);

    public PullRecipeInputsPacket(boolean maxTransfer, boolean craftMissing, List<RequestedIngredient> requestedIngredients) {
        this.maxTransfer = maxTransfer;
        this.craftMissing = craftMissing;
        this.requestedIngredients = requestedIngredients.stream().map(RequestedIngredient::copy).toList();
    }

    private static PullRecipeInputsPacket decode(RegistryFriendlyByteBuf buffer) {
        boolean maxTransfer = buffer.readBoolean();
        boolean craftMissing = buffer.readBoolean();
        return new PullRecipeInputsPacket(maxTransfer, craftMissing,
                RecipeTransferPacketHelper.readRequestedIngredients(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(maxTransfer);
        buffer.writeBoolean(craftMissing);
        RecipeTransferPacketHelper.writeRequestedIngredients(buffer, requestedIngredients);
    }

    @Override
    public Type<PullRecipeInputsPacket> type() {
        return TYPE;
    }

    public record RequestedIngredient(List<ItemStack> alternatives, int count) {
        public RequestedIngredient(List<ItemStack> alternatives, int count) {
            this.alternatives = alternatives.stream().map(ItemStack::copy).toList();
            this.count = count;
        }

        public RequestedIngredient copy() {
            return new RequestedIngredient(alternatives, count);
        }
    }
}
