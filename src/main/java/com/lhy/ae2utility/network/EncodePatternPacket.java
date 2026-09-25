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

public record EncodePatternPacket(List<List<GenericStack>> inputs, List<GenericStack> outputs, @Nullable ResourceLocation recipeId,
        String patternName, String providerSearchKey, String providerDisplayName, boolean shiftDown, boolean substitute, boolean substituteFluids,
        boolean preserveInputOrder, boolean jeiSequentialQueue, boolean jeiFullCategoryBatch, int bulkEncodeSessionId,
        boolean craftingCategoryHint, @Nullable ResourceLocation omniversalRecipeId, String omniversalFingerprint,
        String omniversalSourceId)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EncodePatternPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "encode_pattern"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EncodePatternPacket> STREAM_CODEC =
            StreamCodec.ofMember(EncodePatternPacket::write, EncodePatternPacket::decode);

    public EncodePatternPacket(List<List<GenericStack>> inputs, List<GenericStack> outputs, @Nullable ResourceLocation recipeId,
            String patternName, String providerSearchKey, String providerDisplayName, boolean shiftDown, boolean substitute,
            boolean substituteFluids, boolean preserveInputOrder, boolean jeiSequentialQueue, boolean jeiFullCategoryBatch,
            int bulkEncodeSessionId, boolean craftingCategoryHint, @Nullable ResourceLocation omniversalRecipeId,
            String omniversalFingerprint, String omniversalSourceId) {
        this.inputs = Collections.unmodifiableList(new ArrayList<>(inputs));
        this.outputs = Collections.unmodifiableList(new ArrayList<>(outputs));
        this.recipeId = recipeId;
        this.patternName = patternName == null ? "" : patternName;
        this.providerSearchKey = providerSearchKey == null ? "" : providerSearchKey;
        this.providerDisplayName = providerDisplayName == null ? "" : providerDisplayName;
        this.shiftDown = shiftDown;
        this.substitute = substitute;
        this.substituteFluids = substituteFluids;
        this.preserveInputOrder = preserveInputOrder;
        this.jeiSequentialQueue = jeiSequentialQueue;
        this.jeiFullCategoryBatch = jeiFullCategoryBatch;
        this.bulkEncodeSessionId = bulkEncodeSessionId;
        this.craftingCategoryHint = craftingCategoryHint;
        this.omniversalRecipeId = omniversalRecipeId;
        this.omniversalFingerprint = omniversalFingerprint == null ? "" : omniversalFingerprint;
        this.omniversalSourceId = omniversalSourceId == null ? "" : omniversalSourceId;
    }

    /** 便捷重载：不带万象样板身份。 */
    public EncodePatternPacket(List<List<GenericStack>> inputs, List<GenericStack> outputs, @Nullable ResourceLocation recipeId,
            String patternName, String providerSearchKey, String providerDisplayName, boolean shiftDown, boolean substitute,
            boolean substituteFluids, boolean preserveInputOrder, boolean jeiSequentialQueue, boolean jeiFullCategoryBatch,
            int bulkEncodeSessionId, boolean craftingCategoryHint) {
        this(inputs, outputs, recipeId, patternName, providerSearchKey, providerDisplayName, shiftDown, substitute, substituteFluids,
                preserveInputOrder, jeiSequentialQueue, jeiFullCategoryBatch, bulkEncodeSessionId, craftingCategoryHint,
                null, "", "");
    }

    /** 便捷重载：不带 craftingCategoryHint（默认 false）。 */
    public EncodePatternPacket(List<List<GenericStack>> inputs, List<GenericStack> outputs, @Nullable ResourceLocation recipeId,
            String patternName, String providerSearchKey, String providerDisplayName, boolean shiftDown, boolean substitute,
            boolean substituteFluids, boolean preserveInputOrder, boolean jeiSequentialQueue, boolean jeiFullCategoryBatch,
            int bulkEncodeSessionId) {
        this(inputs, outputs, recipeId, patternName, providerSearchKey, providerDisplayName, shiftDown, substitute, substituteFluids,
                preserveInputOrder, jeiSequentialQueue, jeiFullCategoryBatch, bulkEncodeSessionId, false);
    }

    @Deprecated(forRemoval = false)
    public EncodePatternPacket(List<List<GenericStack>> inputs, List<GenericStack> outputs, @Nullable ResourceLocation recipeId,
            String patternName, String providerSearchKey, String providerDisplayName, boolean shiftDown, boolean substitute,
            boolean substituteFluids, boolean preserveInputOrder) {
        this(inputs, outputs, recipeId, patternName, providerSearchKey, providerDisplayName, shiftDown, substitute, substituteFluids,
                preserveInputOrder, false, false, 0);
    }

    /** 若「同一批批量编码」会话 id 与原包不同（例如补上 BulkEncodeSessions.next()），拷贝生成新数据包。 */
    public EncodePatternPacket withBulkEncodeSessionId(int newBulkEncodeSessionId) {
        return copyWith(shiftDown, jeiSequentialQueue, jeiFullCategoryBatch, newBulkEncodeSessionId);
    }

    public EncodePatternPacket withSequentialUpload(boolean sequential) {
        return copyWith(shiftDown, sequential, jeiFullCategoryBatch, bulkEncodeSessionId);
    }

    public EncodePatternPacket withEncodeFlags(boolean shift, boolean sequential, boolean fullCategory, int bulkSid) {
        return copyWith(shift, sequential, fullCategory, bulkSid);
    }

    public EncodePatternPacket withOmniversalIdentity(@Nullable ResourceLocation omniversalId, String fingerprint, String sourceId) {
        return new EncodePatternPacket(inputs, outputs, recipeId, patternName, providerSearchKey, providerDisplayName, shiftDown,
                substitute, substituteFluids, preserveInputOrder, jeiSequentialQueue, jeiFullCategoryBatch, bulkEncodeSessionId,
                craftingCategoryHint, omniversalId, fingerprint, sourceId);
    }

    public boolean hasOmniversalIdentity() {
        return omniversalRecipeId != null && !omniversalFingerprint.isBlank();
    }

    private EncodePatternPacket copyWith(boolean shift, boolean sequential, boolean fullCategory, int bulkSid) {
        return new EncodePatternPacket(inputs, outputs, recipeId, patternName, providerSearchKey, providerDisplayName, shift,
                substitute, substituteFluids, preserveInputOrder, sequential, fullCategory, bulkSid, craftingCategoryHint,
                omniversalRecipeId, omniversalFingerprint, omniversalSourceId);
    }

    private static EncodePatternPacket decode(RegistryFriendlyByteBuf buffer) {
        boolean hasId = buffer.readBoolean();
        ResourceLocation id = hasId ? buffer.readResourceLocation() : null;
        String patternName = buffer.readUtf(256);
        String providerSearchKey = buffer.readUtf(256);
        String providerDisplayName = buffer.readUtf(256);
        boolean shiftDown = buffer.readableBytes() > 0 && buffer.readBoolean();
        boolean substitute = buffer.readableBytes() > 0 && buffer.readBoolean();
        boolean substituteFluids = buffer.readableBytes() > 0 && buffer.readBoolean();
        boolean preserveInputOrder = buffer.readableBytes() > 0 && buffer.readBoolean();

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
        boolean jeiSequentialQueue = buffer.readableBytes() > 0 && buffer.readBoolean();
        boolean jeiFullCategoryBatch = buffer.readableBytes() > 0 && buffer.readBoolean();
        int bulkEncodeSessionId = buffer.readableBytes() > 0 ? buffer.readVarInt() : 0;
        boolean craftingCategoryHint = buffer.readableBytes() > 0 && buffer.readBoolean();
        ResourceLocation omniversalRecipeId = null;
        String omniversalFingerprint = "";
        String omniversalSourceId = "";
        if (buffer.readableBytes() > 0) {
            if (buffer.readBoolean()) {
                omniversalRecipeId = buffer.readResourceLocation();
            }
            omniversalFingerprint = buffer.readUtf(1024);
            omniversalSourceId = buffer.readUtf(256);
        }
        return new EncodePatternPacket(inputs, outputs, id, patternName, providerSearchKey, providerDisplayName, shiftDown, substitute,
                substituteFluids, preserveInputOrder, jeiSequentialQueue, jeiFullCategoryBatch, bulkEncodeSessionId, craftingCategoryHint,
                omniversalRecipeId, omniversalFingerprint, omniversalSourceId);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(recipeId != null);
        if (recipeId != null) {
            buffer.writeResourceLocation(recipeId);
        }
        buffer.writeUtf(patternName, 256);
        buffer.writeUtf(providerSearchKey, 256);
        buffer.writeUtf(providerDisplayName, 256);
        buffer.writeBoolean(shiftDown);
        buffer.writeBoolean(substitute);
        buffer.writeBoolean(substituteFluids);
        buffer.writeBoolean(preserveInputOrder);

        List<List<GenericStack>> encodedInputs = inputs;
        List<GenericStack> encodedOutputs = outputs;
        if (!canEncodeStacks(inputs, outputs)) {
            encodedInputs = List.of();
            encodedOutputs = List.of();
        }

        buffer.writeVarInt(encodedInputs.size());
        for (List<GenericStack> slotInputs : encodedInputs) {
            buffer.writeBoolean(slotInputs != null);
            if (slotInputs != null) {
                writeGenericStacks(buffer, slotInputs);
            }
        }

        writeGenericStacks(buffer, encodedOutputs);
        buffer.writeBoolean(jeiSequentialQueue);
        buffer.writeBoolean(jeiFullCategoryBatch);
        buffer.writeVarInt(bulkEncodeSessionId);
        buffer.writeBoolean(craftingCategoryHint);
        buffer.writeBoolean(omniversalRecipeId != null);
        if (omniversalRecipeId != null) {
            buffer.writeResourceLocation(omniversalRecipeId);
        }
        buffer.writeUtf(omniversalFingerprint, 1024);
        buffer.writeUtf(omniversalSourceId, 256);
    }

    private static List<GenericStack> readGenericStacks(RegistryFriendlyByteBuf buffer, String field) {
        int size = NetworkValidation.readListSize(buffer, NetworkValidation.MAX_STACKS_PER_SLOT, field);
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

    static boolean canEncodeStacks(List<List<GenericStack>> inputs, List<GenericStack> outputs) {
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
            if (totalStacks > NetworkValidation.MAX_TOTAL_PATTERN_STACKS) {
                return false;
            }
        }
        return totalStacks <= NetworkValidation.MAX_TOTAL_PATTERN_STACKS;
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
