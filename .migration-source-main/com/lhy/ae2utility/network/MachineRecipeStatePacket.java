package com.lhy.ae2utility.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.lhy.ae2utility.Ae2UtilityMod;

public record MachineRecipeStatePacket(int containerId, String profileId, String requestKey, State state,
        List<IngredientAvailability> availability) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MachineRecipeStatePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "machine_recipe_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MachineRecipeStatePacket> STREAM_CODEC =
            StreamCodec.ofMember(MachineRecipeStatePacket::write, MachineRecipeStatePacket::decode);

    public MachineRecipeStatePacket(int containerId, String profileId, String requestKey, State state, List<IngredientAvailability> availability) {
        this.containerId = containerId;
        this.profileId = profileId;
        this.requestKey = requestKey;
        this.state = state;
        this.availability = availability.stream().map(IngredientAvailability::copy).toList();
    }

    private static MachineRecipeStatePacket decode(RegistryFriendlyByteBuf buffer) {
        int containerId = buffer.readVarInt();
        String profileId = buffer.readUtf();
        String requestKey = buffer.readUtf();
        State state = State.values()[buffer.readVarInt()];
        int size = buffer.readVarInt();
        List<IngredientAvailability> availability = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            availability.add(IngredientAvailability.decode(buffer));
        }
        return new MachineRecipeStatePacket(containerId, profileId, requestKey, state, availability);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeUtf(profileId);
        buffer.writeUtf(requestKey);
        buffer.writeVarInt(state.ordinal());
        buffer.writeVarInt(availability.size());
        for (IngredientAvailability entry : availability) {
            entry.write(buffer);
        }
    }

    @Override
    public Type<MachineRecipeStatePacket> type() {
        return TYPE;
    }

    public enum State {
        READY,
        NO_WIRELESS,
        DISCONNECTED
    }

    public record IngredientAvailability(ItemStack stack, int availableAmount) {
        public IngredientAvailability(ItemStack stack, int availableAmount) {
            this.stack = stack.copy();
            this.availableAmount = availableAmount;
        }

        private static IngredientAvailability decode(RegistryFriendlyByteBuf buffer) {
            return new IngredientAvailability(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readVarInt());
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack);
            buffer.writeVarInt(availableAmount);
        }

        public IngredientAvailability copy() {
            return new IngredientAvailability(stack, availableAmount);
        }
    }
}
