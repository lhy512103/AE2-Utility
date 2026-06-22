package com.lhy.ae2utility.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.GenericStack;

/**
 * 客户端 → 服务端：请求编码一条 JEI 配方为样板（并可选 Shift 上传到 EAEP）。
 *
 * <p>1.20.1 forge 版本精简：去掉批量 / 顺序队列等附属字段，
 * 仅保留编码 + EAEP 上传所需的核心数据。</p>
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

    public EncodePatternPacket(List<List<GenericStack>> inputs, List<GenericStack> outputs, @Nullable ResourceLocation recipeId,
            String patternName, String providerSearchKey, boolean shiftDown, boolean substitute,
            boolean substituteFluids, boolean preserveInputOrder, boolean craftingCategoryHint) {
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

        int inputsSize = buffer.readVarInt();
        List<List<GenericStack>> inputs = new ArrayList<>(inputsSize);
        for (int i = 0; i < inputsSize; i++) {
            if (buffer.readBoolean()) {
                inputs.add(readGenericStacks(buffer));
            } else {
                inputs.add(null);
            }
        }

        List<GenericStack> outputs = readGenericStacks(buffer);
        return new EncodePatternPacket(inputs, outputs, id, patternName, providerSearchKey, shiftDown, substitute,
                substituteFluids, preserveInputOrder, craftingCategoryHint);
    }

    private static List<GenericStack> readGenericStacks(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
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
