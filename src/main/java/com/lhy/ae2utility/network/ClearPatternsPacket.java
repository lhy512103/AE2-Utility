package com.lhy.ae2utility.network;

import net.minecraft.network.FriendlyByteBuf;

public class ClearPatternsPacket {
    public static final ClearPatternsPacket INSTANCE = new ClearPatternsPacket();

    public static void encode(ClearPatternsPacket msg, FriendlyByteBuf buffer) {
    }

    public static ClearPatternsPacket decode(FriendlyByteBuf buffer) {
        return INSTANCE;
    }
}
