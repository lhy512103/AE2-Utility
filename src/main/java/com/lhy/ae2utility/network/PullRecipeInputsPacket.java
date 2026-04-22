package com.lhy.ae2utility.network;

import java.util.ArrayList;
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
        int size = buffer.readVarInt();
        List<RequestedIngredient> ingredients = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ingredients.add(RequestedIngredient.decode(buffer));
        }
        return new PullRecipeInputsPacket(maxTransfer, craftMissing, ingredients);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(maxTransfer);
        buffer.writeBoolean(craftMissing);
        buffer.writeVarInt(requestedIngredients.size());
        for (RequestedIngredient ingredient : requestedIngredients) {
            ingredient.write(buffer);
        }
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

        private static RequestedIngredient decode(RegistryFriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            List<ItemStack> alternatives = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                alternatives.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
            }
            return new RequestedIngredient(alternatives, buffer.readVarInt());
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(alternatives.size());
            for (ItemStack alternative : alternatives) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, alternative);
            }
            buffer.writeVarInt(count);
        }

        public RequestedIngredient copy() {
            return new RequestedIngredient(alternatives, count);
        }
    }
}
