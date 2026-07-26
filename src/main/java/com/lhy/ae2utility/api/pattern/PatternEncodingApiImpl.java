package com.lhy.ae2utility.api.pattern;

import com.lhy.ae2utility.service.EncodePatternService;

import net.minecraft.server.level.ServerPlayer;

final class PatternEncodingApiImpl implements PatternEncodingApi {
    @Override
    public PatternEncodingResult encode(ServerPlayer player, PatternEncodingRequest request) {
        if (player == null || request == null) {
            return PatternEncodingResult.INVALID_REQUEST;
        }
        if (player.getServer() == null || !player.getServer().isSameThread()) {
            return PatternEncodingResult.WRONG_SIDE;
        }
        EncodePatternService.handleApi(player, request);
        return PatternEncodingResult.ACCEPTED;
    }

    @Override
    public PatternEncodingResult encodeBatch(ServerPlayer player, PatternEncodingBatch batch) {
        if (player == null || batch == null || batch.requests().isEmpty()) {
            return PatternEncodingResult.INVALID_REQUEST;
        }
        if (player.getServer() == null || !player.getServer().isSameThread()) {
            return PatternEncodingResult.WRONG_SIDE;
        }
        EncodePatternService.handleApiBatch(player, batch);
        return PatternEncodingResult.ACCEPTED;
    }
}