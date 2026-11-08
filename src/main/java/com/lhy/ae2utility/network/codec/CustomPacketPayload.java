package com.lhy.ae2utility.network.codec;

import net.minecraft.resources.ResourceLocation;

/** Typed identity for messages carried by the Forge SimpleChannel. */
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();
    record Type<T extends CustomPacketPayload>(ResourceLocation id) {}
}
