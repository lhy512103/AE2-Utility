package com.lhy.ae2utility.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;

/** 客户端 → 服务端：尝试把背包中这些槽位的已编码样板上传到装配矩阵，剩余的由服务端回传。 */
public record UploadInventoryPatternsPacket(List<Integer> slots) {
    public static final int MAX_SLOTS = 128;

    public static void encode(UploadInventoryPatternsPacket msg, FriendlyByteBuf buffer) {
        writeSlots(buffer, msg.slots);
    }

    public static UploadInventoryPatternsPacket decode(FriendlyByteBuf buffer) {
        return new UploadInventoryPatternsPacket(readSlots(buffer));
    }

    static void writeSlots(FriendlyByteBuf buffer, List<Integer> slots) {
        int size = Math.min(slots.size(), MAX_SLOTS);
        buffer.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            buffer.writeVarInt(slots.get(i));
        }
    }

    static List<Integer> readSlots(FriendlyByteBuf buffer) {
        int size = NetworkValidation.readListSize(buffer, MAX_SLOTS, "inventory slots");
        List<Integer> slots = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            slots.add(buffer.readVarInt());
        }
        return slots;
    }
}
