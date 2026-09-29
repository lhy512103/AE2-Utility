package com.lhy.ae2utility.network;

import net.minecraft.network.FriendlyByteBuf;

/** 悬停 JEI 编码箭头时，请服务端按真正编码路径算出样板内容并回传（不消耗空白样板）。 */
public record QueryEncodePreviewPacket(int requestId, EncodePatternPacket recipe) {
    public static void encode(QueryEncodePreviewPacket msg, FriendlyByteBuf buffer) {
        buffer.writeVarInt(msg.requestId);
        EncodePatternPacket.encode(msg.recipe, buffer);
    }

    public static QueryEncodePreviewPacket decode(FriendlyByteBuf buffer) {
        int requestId = buffer.readVarInt();
        return new QueryEncodePreviewPacket(requestId, EncodePatternPacket.decode(buffer));
    }
}
