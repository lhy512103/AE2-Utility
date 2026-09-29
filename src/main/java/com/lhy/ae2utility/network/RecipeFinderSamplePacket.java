package com.lhy.ae2utility.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/** 客户端 → 服务端：从 JEI 拖入配方查找器样本格的物品。 */
public record RecipeFinderSamplePacket(ItemStack stack) {
    public static void encode(RecipeFinderSamplePacket msg, FriendlyByteBuf buffer) {
        buffer.writeItem(msg.stack);
    }

    public static RecipeFinderSamplePacket decode(FriendlyByteBuf buffer) {
        return new RecipeFinderSamplePacket(buffer.readItem());
    }
}
