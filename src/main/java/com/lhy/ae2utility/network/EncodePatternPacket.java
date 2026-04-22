package com.lhy.ae2utility.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.GenericStack;
import com.lhy.ae2utility.Ae2UtilityMod;

public record EncodePatternPacket(List<List<GenericStack>> inputs, List<GenericStack> outputs, @Nullable ResourceLocation recipeId, boolean shiftDown, boolean substitute, boolean substituteFluids) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EncodePatternPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "encode_pattern"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EncodePatternPacket> STREAM_CODEC =
            StreamCodec.ofMember(EncodePatternPacket::write, EncodePatternPacket::decode);

    public EncodePatternPacket(List<List<GenericStack>> inputs, List<GenericStack> outputs, @Nullable ResourceLocation recipeId, boolean shiftDown, boolean substitute, boolean substituteFluids) {
        this.inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
        this.outputs = Collections.unmodifiableList(new ArrayList<>(outputs));
        this.recipeId = recipeId;
        this.shiftDown = shiftDown;
        this.substitute = substitute;
        this.substituteFluids = substituteFluids;
    }

    private static EncodePatternPacket decode(RegistryFriendlyByteBuf buffer) {
        boolean hasId = buffer.readBoolean();
        ResourceLocation id = hasId ? buffer.readResourceLocation() : null;
        boolean shiftDown = buffer.readableBytes() > 0 && buffer.readBoolean();
        boolean substitute = buffer.readableBytes() > 0 && buffer.readBoolean();
        boolean substituteFluids = buffer.readableBytes() > 0 && buffer.readBoolean();
        
        int inputsSize = buffer.readVarInt();
        List<List<GenericStack>> inputs = new ArrayList<>(inputsSize);
        for (int i = 0; i < inputsSize; i++) {
            if (buffer.readBoolean()) {
                inputs.add(readGenericStacks(buffer));
            } else {
                inputs.add(null);
            }
        }
        
        return new EncodePatternPacket(
                inputs,
                readGenericStacks(buffer),
                id,
                shiftDown,
                substitute,
                substituteFluids
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(recipeId != null);
        if (recipeId != null) {
            buffer.writeResourceLocation(recipeId);
        }
        buffer.writeBoolean(shiftDown);
        buffer.writeBoolean(substitute);
        buffer.writeBoolean(substituteFluids);
        
        buffer.writeVarInt(inputs.size());
        for (List<GenericStack> slotInputs : inputs) {
            buffer.writeBoolean(slotInputs != null);
            if (slotInputs != null) {
                writeGenericStacks(buffer, slotInputs);
            }
        }
        
        writeGenericStacks(buffer, outputs);
    }
    
    private static List<GenericStack> readGenericStacks(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<GenericStack> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            if (buffer.readBoolean()) {
                list.add(GenericStack.STREAM_CODEC.decode(buffer));
            } else {
                list.add(null);
            }
        }
        return list;
    }
    
    private static void writeGenericStacks(RegistryFriendlyByteBuf buffer, List<GenericStack> stacks) {
        buffer.writeVarInt(stacks.size());
        for (GenericStack stack : stacks) {
            buffer.writeBoolean(stack != null);
            if (stack != null) {
                GenericStack.STREAM_CODEC.encode(buffer, stack);
            }
        }
    }

    @Override
    public Type<EncodePatternPacket> type() {
        return TYPE;
    }
}

