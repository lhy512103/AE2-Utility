package com.lhy.ae2utility.network.codec;

import appeng.api.stacks.GenericStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public final class ForgeCodecs {
    private ForgeCodecs() {}
    public static final StreamCodec<FriendlyByteBuf,ItemStack> ITEM_STACK =
            StreamCodec.of(FriendlyByteBuf::writeItem,FriendlyByteBuf::readItem);
    public static final StreamCodec<FriendlyByteBuf,GenericStack> GENERIC_STACK =
            StreamCodec.ofMember(GenericStack::writeBuffer,GenericStack::readBuffer);
}
