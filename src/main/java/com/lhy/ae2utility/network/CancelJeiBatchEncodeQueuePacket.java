package com.lhy.ae2utility.network;

import com.lhy.ae2utility.jei.JeiBatchEncodeQueue;

import net.minecraft.network.FriendlyByteBuf;

public class CancelJeiBatchEncodeQueuePacket {
    public static final CancelJeiBatchEncodeQueuePacket INSTANCE = new CancelJeiBatchEncodeQueuePacket();

    public static void encode(CancelJeiBatchEncodeQueuePacket msg, FriendlyByteBuf buffer) {
    }

    public static CancelJeiBatchEncodeQueuePacket decode(FriendlyByteBuf buffer) {
        return INSTANCE;
    }

    public static void handle(CancelJeiBatchEncodeQueuePacket msg) {
        JeiBatchEncodeQueue.cancelFromServer();
    }
}
