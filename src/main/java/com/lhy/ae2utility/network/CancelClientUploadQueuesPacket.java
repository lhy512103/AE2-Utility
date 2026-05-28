package com.lhy.ae2utility.network;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.client.InventoryPatternUploadQueue;
import com.lhy.ae2utility.client.RecipeTreeUploadQueue;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端在强制中止样板上传会话时下发，清空客户端编排队列但不假设施端已完成的编码/入库被撤销。
 */
public record CancelClientUploadQueuesPacket(boolean stoppedByOperatorForEveryone)
        implements CustomPacketPayload {

    public static final Type<CancelClientUploadQueuesPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "cancel_client_upload_queues"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CancelClientUploadQueuesPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeBoolean(p.stoppedByOperatorForEveryone),
            buf -> new CancelClientUploadQueuesPacket(buf.readBoolean()));

    @Override
    public Type<CancelClientUploadQueuesPacket> type() {
        return TYPE;
    }

    public static void handle(CancelClientUploadQueuesPacket payload) {
        RecipeTreeUploadQueue.cancelAllQuiet();
        InventoryPatternUploadQueue.cancelAllQuiet();
        var player = Minecraft.getInstance().player;
        if (payload.stoppedByOperatorForEveryone() && player != null) {
            player.displayClientMessage(
                    Component.translatable("message.ae2utility.uploads_stopped_by_admin").withStyle(ChatFormatting.GOLD),
                    false);
        }
    }
}
