package com.lhy.ae2utility.api.pattern;

import net.minecraft.server.level.ServerPlayer;

/** Server-side pattern encoding operations. */
public interface PatternEncodingApi {
    PatternEncodingApi INSTANCE = new PatternEncodingApiImpl();

    PatternEncodingResult encode(ServerPlayer player, PatternEncodingRequest request);

    PatternEncodingResult encodeBatch(ServerPlayer player, PatternEncodingBatch batch);
}