package com.lhy.ae2utility.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import net.minecraft.network.FriendlyByteBuf;

import appeng.api.stacks.GenericStack;

import com.lhy.ae2utility.jei.EncodePreviewCache;
import com.lhy.ae2utility.util.PatternEncodingPreview;
import com.lhy.ae2utility.util.PatternEncodingPreviewMode;

/** 服务端编码预览结果：按 JEI 输入槽顺序的实际编码材料 + 编码终端面板布局。 */
public record EncodePreviewResultPacket(
        int requestId,
        List<GenericStack> inputs,
        PatternEncodingPreviewMode mode,
        List<GenericStack> panelInputs,
        List<GenericStack> panelOutputs,
        boolean substituteItems,
        boolean substituteFluids,
        int extraInputs,
        int extraOutputs) {
    private static final int MAX_PANEL_OUTPUTS = 27;
    private static final List<Consumer<EncodePreviewResultPacket>> CLIENT_LISTENERS = new CopyOnWriteArrayList<>();

    public static void addListener(Consumer<EncodePreviewResultPacket> listener) {
        CLIENT_LISTENERS.add(listener);
    }

    public EncodePreviewResultPacket {
        inputs = copyStacks(inputs);
        panelInputs = copyStacks(panelInputs);
        panelOutputs = copyStacks(panelOutputs);
        mode = mode == null ? PatternEncodingPreviewMode.PROCESSING : mode;
    }

    public static EncodePreviewResultPacket empty(int requestId) {
        return new EncodePreviewResultPacket(requestId, List.of(), PatternEncodingPreviewMode.PROCESSING,
                List.of(), List.of(), false, false, 0, 0);
    }

    public static EncodePreviewResultPacket of(int requestId, List<GenericStack> inputs, PatternEncodingPreview panel) {
        return new EncodePreviewResultPacket(requestId, inputs, panel.mode(), panel.inputs(), panel.outputs(),
                panel.substituteItems(), panel.substituteFluids(), panel.extraInputs(), panel.extraOutputs());
    }

    public boolean hasPanel() {
        return PatternEncodingPreview.presentCount(panelInputs) > 0
                && PatternEncodingPreview.presentCount(panelOutputs) > 0;
    }

    public PatternEncodingPreview panelPreview() {
        return new PatternEncodingPreview(mode, panelInputs, panelOutputs, substituteItems, substituteFluids,
                extraInputs, extraOutputs);
    }

    public static void encode(EncodePreviewResultPacket msg, FriendlyByteBuf buffer) {
        buffer.writeVarInt(msg.requestId);
        writeStacks(buffer, msg.inputs, NetworkValidation.MAX_RECIPE_INPUT_SLOTS);
        buffer.writeByte(msg.mode.ordinal());
        writeStacks(buffer, msg.panelInputs, NetworkValidation.MAX_RECIPE_INPUT_SLOTS);
        writeStacks(buffer, msg.panelOutputs, MAX_PANEL_OUTPUTS);
        buffer.writeBoolean(msg.substituteItems);
        buffer.writeBoolean(msg.substituteFluids);
        buffer.writeVarInt(msg.extraInputs);
        buffer.writeVarInt(msg.extraOutputs);
    }

    public static EncodePreviewResultPacket decode(FriendlyByteBuf buffer) {
        int requestId = buffer.readVarInt();
        List<GenericStack> inputs = readStacks(buffer, NetworkValidation.MAX_RECIPE_INPUT_SLOTS, "preview inputs");
        PatternEncodingPreviewMode mode = PatternEncodingPreviewMode.byOrdinal(buffer.readByte());
        List<GenericStack> panelInputs = readStacks(buffer, NetworkValidation.MAX_RECIPE_INPUT_SLOTS, "preview panel inputs");
        List<GenericStack> panelOutputs = readStacks(buffer, MAX_PANEL_OUTPUTS, "preview panel outputs");
        boolean substituteItems = buffer.readBoolean();
        boolean substituteFluids = buffer.readBoolean();
        int extraInputs = Math.max(0, buffer.readVarInt());
        int extraOutputs = Math.max(0, buffer.readVarInt());
        return new EncodePreviewResultPacket(requestId, inputs, mode, panelInputs, panelOutputs,
                substituteItems, substituteFluids, extraInputs, extraOutputs);
    }

    public static void handle(EncodePreviewResultPacket msg) {
        EncodePreviewCache.handleResult(msg);
        CLIENT_LISTENERS.forEach(listener -> listener.accept(msg));
    }

    private static List<GenericStack> readStacks(FriendlyByteBuf buffer, int max, String field) {
        int size = NetworkValidation.readListSize(buffer, max, field);
        List<GenericStack> stacks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            stacks.add(buffer.readBoolean() ? GenericStack.readBuffer(buffer) : null);
        }
        return stacks;
    }

    private static void writeStacks(FriendlyByteBuf buffer, List<GenericStack> stacks, int max) {
        int size = Math.min(stacks.size(), max);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            GenericStack stack = stacks.get(i);
            boolean present = stack != null && stack.what() != null;
            buffer.writeBoolean(present);
            if (present) {
                GenericStack.writeBuffer(stack, buffer);
            }
        }
    }

    private static List<GenericStack> copyStacks(List<GenericStack> stacks) {
        return stacks == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(stacks));
    }
}
