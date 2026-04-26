package com.lhy.ae2utility.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.lhy.ae2utility.Ae2UtilityMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UploadInventoryPatternsToMatrixPacket(List<Integer> slotIndices) implements CustomPacketPayload {
    public static final Type<UploadInventoryPatternsToMatrixPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "upload_inventory_patterns_to_matrix"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UploadInventoryPatternsToMatrixPacket> STREAM_CODEC =
            StreamCodec.ofMember(UploadInventoryPatternsToMatrixPacket::write, UploadInventoryPatternsToMatrixPacket::decode);

    public UploadInventoryPatternsToMatrixPacket(List<Integer> slotIndices) {
        this.slotIndices = Collections.unmodifiableList(new ArrayList<>(slotIndices));
    }

    private static @NotNull UploadInventoryPatternsToMatrixPacket decode(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<Integer> slots = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            slots.add(buffer.readVarInt());
        }
        return new UploadInventoryPatternsToMatrixPacket(slots);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(slotIndices.size());
        for (Integer slotIndex : slotIndices) {
            buffer.writeVarInt(slotIndex == null ? -1 : slotIndex.intValue());
        }
    }

    @Override
    public Type<UploadInventoryPatternsToMatrixPacket> type() {
        return TYPE;
    }
}
