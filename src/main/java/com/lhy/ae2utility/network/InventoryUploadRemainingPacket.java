package com.lhy.ae2utility.network;

import java.util.List;

import net.minecraft.network.FriendlyByteBuf;

import com.lhy.ae2utility.client.InventoryPatternUpload;

/** 服务端 → 客户端：未能写入装配矩阵、需要选择供应器的背包样板槽位。 */
public record InventoryUploadRemainingPacket(List<Integer> slots) {
    public static void encode(InventoryUploadRemainingPacket msg, FriendlyByteBuf buffer) {
        UploadInventoryPatternsPacket.writeSlots(buffer, msg.slots);
    }

    public static InventoryUploadRemainingPacket decode(FriendlyByteBuf buffer) {
        return new InventoryUploadRemainingPacket(UploadInventoryPatternsPacket.readSlots(buffer));
    }

    public static void handle(InventoryUploadRemainingPacket msg) {
        InventoryPatternUpload.onRemaining(msg.slots);
    }
}
