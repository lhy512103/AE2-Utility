package com.lhy.ae2utility.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.GenericStack;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.client.EncodePreviewClientDispatch;
import com.lhy.ae2utility.util.PatternEncodingPreview;
import com.lhy.ae2utility.util.PatternEncodingPreviewMode;

/**
 * Server-side encode preview: JEI slot-order inputs plus the AE2 encoding-terminal layout.
 */
public record EncodePreviewResultPacket(
        int requestId,
        List<GenericStack> inputs,
        PatternEncodingPreviewMode mode,
        List<GenericStack> panelInputs,
        List<GenericStack> panelOutputs,
        boolean substituteItems,
        boolean substituteFluids,
        int extraInputs,
        int extraOutputs) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EncodePreviewResultPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "encode_preview_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EncodePreviewResultPacket> STREAM_CODEC =
            StreamCodec.ofMember(EncodePreviewResultPacket::write, EncodePreviewResultPacket::decode);

    public static EncodePreviewResultPacket empty(int requestId) {
        return new EncodePreviewResultPacket(requestId, List.of(), PatternEncodingPreviewMode.PROCESSING,
                List.of(), List.of(), false, false, 0, 0);
    }

    public EncodePreviewResultPacket {
        inputs = copyStacks(inputs);
        panelInputs = copyStacks(panelInputs);
        panelOutputs = copyStacks(panelOutputs);
        extraInputs = Math.max(0, extraInputs);
        extraOutputs = Math.max(0, extraOutputs);
        mode = mode == null ? PatternEncodingPreviewMode.PROCESSING : mode;
    }

    public boolean hasPanel() {
        return PatternEncodingPreview.presentCount(panelInputs) > 0
                && PatternEncodingPreview.presentCount(panelOutputs) > 0;
    }

    public PatternEncodingPreview panelPreview() {
        return new PatternEncodingPreview(mode, panelInputs, panelOutputs, substituteItems, substituteFluids,
                extraInputs, extraOutputs);
    }

    private static EncodePreviewResultPacket decode(RegistryFriendlyByteBuf buffer) {
        int requestId = buffer.readVarInt();
        List<GenericStack> inputs = readStacks(buffer, NetworkValidation.MAX_RECIPE_INPUT_SLOTS, "preview inputs");
        PatternEncodingPreviewMode mode = PatternEncodingPreviewMode.byOrdinal(buffer.readByte());
        List<GenericStack> panelInputs = readStacks(buffer, NetworkValidation.MAX_RECIPE_INPUT_SLOTS, "preview panel inputs");
        List<GenericStack> panelOutputs = readStacks(buffer, PatternEncodingPreview.VISIBLE_OUTPUTS + 24,
                "preview panel outputs");
        boolean substituteItems = buffer.readBoolean();
        boolean substituteFluids = buffer.readBoolean();
        int extraInputs = Math.max(0, buffer.readVarInt());
        int extraOutputs = Math.max(0, buffer.readVarInt());
        return new EncodePreviewResultPacket(requestId, inputs, mode, panelInputs, panelOutputs,
                substituteItems, substituteFluids, extraInputs, extraOutputs);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(requestId);
        writeStacks(buffer, inputs, NetworkValidation.MAX_RECIPE_INPUT_SLOTS);
        buffer.writeByte(mode.ordinal());
        writeStacks(buffer, panelInputs, NetworkValidation.MAX_RECIPE_INPUT_SLOTS);
        writeStacks(buffer, panelOutputs, PatternEncodingPreview.VISIBLE_OUTPUTS + 24);
        buffer.writeBoolean(substituteItems);
        buffer.writeBoolean(substituteFluids);
        buffer.writeVarInt(extraInputs);
        buffer.writeVarInt(extraOutputs);
    }

    private static List<GenericStack> readStacks(RegistryFriendlyByteBuf buffer, int max, String field) {
        int size = NetworkValidation.readListSize(buffer, max, field);
        List<GenericStack> stacks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            if (buffer.readBoolean()) {
                stacks.add(GenericStack.STREAM_CODEC.decode(buffer));
            } else {
                stacks.add(null);
            }
        }
        return stacks;
    }

    private static void writeStacks(RegistryFriendlyByteBuf buffer, List<GenericStack> stacks, int max) {
        int size = Math.min(stacks.size(), max);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            GenericStack stack = stacks.get(i);
            boolean present = stack != null && stack.what() != null;
            buffer.writeBoolean(present);
            if (present) {
                GenericStack.STREAM_CODEC.encode(buffer, stack);
            }
        }
    }

    private static List<GenericStack> copyStacks(List<GenericStack> stacks) {
        return stacks == null ? List.of() : java.util.Collections.unmodifiableList(new ArrayList<>(stacks));
    }

    @Override
    public Type<EncodePreviewResultPacket> type() {
        return TYPE;
    }

    public static void handle(EncodePreviewResultPacket payload) {
        EncodePreviewClientDispatch.handle(payload);
    }
}
