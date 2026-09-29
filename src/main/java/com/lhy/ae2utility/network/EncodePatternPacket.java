package com.lhy.ae2utility.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.GenericStack;
import io.netty.buffer.Unpooled;

/**
 * 客户端 → 服务端：请求编码一条 JEI 配方为样板（并可选 Shift 上传到 EAEP）。
 * {@code bulkSessionId} 为 0 表示单条编码，非 0 表示同一次 JEI 批量操作。
 */
public class EncodePatternPacket {
    private final List<List<GenericStack>> inputs;
    private final List<GenericStack> outputs;
    @Nullable
    private final ResourceLocation recipeId;
    private final String patternName;
    private final String providerSearchKey;
    private final boolean shiftDown;
    private final boolean substitute;
    private final boolean substituteFluids;
    private final boolean preserveInputOrder;
    private final boolean craftingCategoryHint;
    private final boolean fullCategoryBatch;
    private final int bulkSessionId;

    public EncodePatternPacket(List<List<GenericStack>> inputs, List<GenericStack> outputs, @Nullable ResourceLocation recipeId,
            String patternName, String providerSearchKey, boolean shiftDown, boolean substitute,
            boolean substituteFluids, boolean preserveInputOrder, boolean craftingCategoryHint) {
        this(inputs, outputs, recipeId, patternName, providerSearchKey, shiftDown, substitute, substituteFluids,
                preserveInputOrder, craftingCategoryHint, false, 0);
    }

    public EncodePatternPacket(List<List<GenericStack>> inputs, List<GenericStack> outputs, @Nullable ResourceLocation recipeId,
            String patternName, String providerSearchKey, boolean shiftDown, boolean substitute,
            boolean substituteFluids, boolean preserveInputOrder, boolean craftingCategoryHint,
            boolean fullCategoryBatch, int bulkSessionId) {
        this.inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
        this.outputs = Collections.unmodifiableList(new ArrayList<>(outputs));
        this.recipeId = recipeId;
        this.patternName = patternName == null ? "" : patternName;
        this.providerSearchKey = providerSearchKey == null ? "" : providerSearchKey;
        this.shiftDown = shiftDown;
        this.substitute = substitute;
        this.substituteFluids = substituteFluids;
        this.preserveInputOrder = preserveInputOrder;
        this.craftingCategoryHint = craftingCategoryHint;
        this.fullCategoryBatch = fullCategoryBatch;
        this.bulkSessionId = bulkSessionId;
    }

    public EncodePatternPacket withBatch(boolean fullCategory, int sessionId) {
        return new EncodePatternPacket(inputs, outputs, recipeId, patternName, providerSearchKey, shiftDown, substitute,
                substituteFluids, preserveInputOrder, craftingCategoryHint, fullCategory, sessionId);
    }

    public List<List<GenericStack>> inputs() {
        return inputs;
    }

    public List<GenericStack> outputs() {
        return outputs;
    }

    @Nullable
    public ResourceLocation recipeId() {
        return recipeId;
    }

    public String patternName() {
        return patternName;
    }

    public String providerSearchKey() {
        return providerSearchKey;
    }

    public boolean shiftDown() {
        return shiftDown;
    }

    public boolean substitute() {
        return substitute;
    }

    public boolean substituteFluids() {
        return substituteFluids;
    }

    public boolean preserveInputOrder() {
        return preserveInputOrder;
    }

    public boolean craftingCategoryHint() {
        return craftingCategoryHint;
    }

    public boolean fullCategoryBatch() {
        return fullCategoryBatch;
    }

    public int bulkSessionId() {
        return bulkSessionId;
    }

    public boolean isBatch() {
        return bulkSessionId != 0;
    }

    public static void encode(EncodePatternPacket msg, FriendlyByteBuf buffer) {
        buffer.writeBoolean(msg.recipeId != null);
        if (msg.recipeId != null) {
            buffer.writeResourceLocation(msg.recipeId);
        }
        buffer.writeUtf(msg.patternName, 256);
        buffer.writeUtf(msg.providerSearchKey, 256);
        buffer.writeBoolean(msg.shiftDown);
        buffer.writeBoolean(msg.substitute);
        buffer.writeBoolean(msg.substituteFluids);
        buffer.writeBoolean(msg.preserveInputOrder);
        buffer.writeBoolean(msg.craftingCategoryHint);
        buffer.writeBoolean(msg.fullCategoryBatch);
        buffer.writeVarInt(msg.bulkSessionId);

        buffer.writeVarInt(msg.inputs.size());
        for (List<GenericStack> slotInputs : msg.inputs) {
            buffer.writeBoolean(slotInputs != null);
            if (slotInputs != null) {
                writeGenericStacks(buffer, slotInputs);
            }
        }

        writeGenericStacks(buffer, msg.outputs);
    }

    public static EncodePatternPacket decode(FriendlyByteBuf buffer) {
        boolean hasId = buffer.readBoolean();
        ResourceLocation id = hasId ? buffer.readResourceLocation() : null;
        String patternName = buffer.readUtf(256);
        String providerSearchKey = buffer.readUtf(256);
        boolean shiftDown = buffer.readBoolean();
        boolean substitute = buffer.readBoolean();
        boolean substituteFluids = buffer.readBoolean();
        boolean preserveInputOrder = buffer.readBoolean();
        boolean craftingCategoryHint = buffer.readBoolean();
        boolean fullCategoryBatch = buffer.readBoolean();
        int bulkSessionId = buffer.readVarInt();

        int inputsSize = NetworkValidation.readListSize(buffer, NetworkValidation.MAX_RECIPE_INPUT_SLOTS, "inputs");
        List<List<GenericStack>> inputs = new ArrayList<>(inputsSize);
        for (int i = 0; i < inputsSize; i++) {
            if (buffer.readBoolean()) {
                inputs.add(readGenericStacks(buffer, "input alternatives"));
            } else {
                inputs.add(null);
            }
        }

        List<GenericStack> outputs = readGenericStacks(buffer, "outputs");
        return new EncodePatternPacket(inputs, outputs, id, patternName, providerSearchKey, shiftDown, substitute,
                substituteFluids, preserveInputOrder, craftingCategoryHint, fullCategoryBatch, bulkSessionId);
    }

    public boolean fitsNetworkLimits() {
        if (inputs.size() > NetworkValidation.MAX_RECIPE_INPUT_SLOTS
                || outputs.size() > NetworkValidation.MAX_STACKS_PER_SLOT) {
            return false;
        }
        long totalStacks = outputs.size();
        for (List<GenericStack> slotInputs : inputs) {
            if (slotInputs == null) {
                continue;
            }
            if (slotInputs.size() > NetworkValidation.MAX_STACKS_PER_SLOT) {
                return false;
            }
            totalStacks += slotInputs.size();
        }
        if (totalStacks > NetworkValidation.MAX_TOTAL_PATTERN_STACKS) {
            return false;
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            encode(this, buffer);
            return buffer.readableBytes() <= NetworkValidation.MAX_SERVERBOUND_PAYLOAD_BYTES;
        } catch (RuntimeException e) {
            return false;
        } finally {
            buffer.release();
        }
    }

    private static List<GenericStack> readGenericStacks(FriendlyByteBuf buffer, String field) {
        int size = NetworkValidation.readListSize(buffer, NetworkValidation.MAX_STACKS_PER_SLOT, field);
        List<GenericStack> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            if (buffer.readBoolean()) {
                list.add(GenericStack.readBuffer(buffer));
            } else {
                list.add(null);
            }
        }
        return list;
    }

    private static void writeGenericStacks(FriendlyByteBuf buffer, List<GenericStack> stacks) {
        buffer.writeVarInt(stacks.size());
        for (GenericStack stack : stacks) {
            buffer.writeBoolean(stack != null);
            if (stack != null) {
                GenericStack.writeBuffer(stack, buffer);
            }
        }
    }
}
