package com.lhy.ae2utility.network;

import net.minecraft.network.FriendlyByteBuf;

/** 客户端 → 服务端：取出背包槽位中的一个样板进入 EAEP 待定上传，并打开供应器选择界面。 */
public record UploadInventoryPatternStepPacket(int slot) {
    public static void encode(UploadInventoryPatternStepPacket msg, FriendlyByteBuf buffer) {
        buffer.writeVarInt(msg.slot);
    }

    public static UploadInventoryPatternStepPacket decode(FriendlyByteBuf buffer) {
        return new UploadInventoryPatternStepPacket(buffer.readVarInt());
    }
}
