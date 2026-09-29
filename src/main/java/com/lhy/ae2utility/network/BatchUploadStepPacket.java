package com.lhy.ae2utility.network;

import net.minecraft.network.FriendlyByteBuf;

import com.lhy.ae2utility.jei.JeiBatchEncodeQueue;

/** 服务端处理完一条批量 Shift 上传后回执：是否已打开 EAEP 供应器选择界面等待玩家选择。 */
public record BatchUploadStepPacket(boolean awaitingProvider) {
    public static void encode(BatchUploadStepPacket msg, FriendlyByteBuf buffer) {
        buffer.writeBoolean(msg.awaitingProvider);
    }

    public static BatchUploadStepPacket decode(FriendlyByteBuf buffer) {
        return new BatchUploadStepPacket(buffer.readBoolean());
    }

    public static void handle(BatchUploadStepPacket msg) {
        JeiBatchEncodeQueue.onServerStep(msg.awaitingProvider);
    }
}
