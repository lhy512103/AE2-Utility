package com.lhy.ae2utility.network;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.client.RecipeTreeUploadQueue;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** @param missingBlankPatternFailure 顺序批量中因 ME / 背包无可用空白样板而失败（服务端 {@code BATCH_ABORT_NO_BLANK}）。 */
public record RecipeTreeUploadResultPacket(String patternName, boolean uploaded, boolean awaitingProviderCompletion,
        boolean abortRemainingBatch, boolean purgeRemainingQueuedSameEaepMachine, boolean missingBlankPatternFailure)
        implements CustomPacketPayload {
    public static final Type<RecipeTreeUploadResultPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "recipe_tree_upload_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeTreeUploadResultPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeUtf(packet.patternName, 256);
                buf.writeBoolean(packet.uploaded);
                buf.writeBoolean(packet.awaitingProviderCompletion);
                buf.writeBoolean(packet.abortRemainingBatch);
                buf.writeBoolean(packet.purgeRemainingQueuedSameEaepMachine);
                buf.writeBoolean(packet.missingBlankPatternFailure);
            }, buf -> {
                String nm = buf.readUtf(256);
                boolean upl = buf.readBoolean();
                boolean aw = buf.readBoolean();
                boolean abrt = buf.readBoolean();
                boolean purge = buf.readBoolean();
                boolean blankFail = buf.readBoolean();
                return new RecipeTreeUploadResultPacket(nm, upl, aw, abrt, purge, blankFail);
            });

    @Override
    public Type<RecipeTreeUploadResultPacket> type() {
        return TYPE;
    }

    public static void handle(RecipeTreeUploadResultPacket packet) {
        RecipeTreeUploadQueue.handleResult(packet);
    }
}
