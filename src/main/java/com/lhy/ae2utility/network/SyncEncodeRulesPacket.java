package com.lhy.ae2utility.network;

import net.minecraft.network.FriendlyByteBuf;

import com.lhy.ae2utility.Ae2UtilityServerConfig;
import com.lhy.ae2utility.client.RemoteEncodeRules;

/** 登录时把服务端编码策略下发给客户端，便于批量按钮预判。 */
public record SyncEncodeRulesPacket(boolean blockFullCategoryBatch, boolean requireOpenEncodingMenu,
        int maxPatternsPerSession) {

    public static SyncEncodeRulesPacket fromServerConfig() {
        return new SyncEncodeRulesPacket(
                Ae2UtilityServerConfig.blockJeiFullCategoryBatchEncode(),
                Ae2UtilityServerConfig.requireOpenPatternEncodingMenuForJei(),
                Ae2UtilityServerConfig.jeiBulkEncodeMaxPatternsPerSession());
    }

    public static void encode(SyncEncodeRulesPacket msg, FriendlyByteBuf buffer) {
        buffer.writeBoolean(msg.blockFullCategoryBatch);
        buffer.writeBoolean(msg.requireOpenEncodingMenu);
        buffer.writeVarInt(msg.maxPatternsPerSession);
    }

    public static SyncEncodeRulesPacket decode(FriendlyByteBuf buffer) {
        return new SyncEncodeRulesPacket(buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt());
    }

    public static void handle(SyncEncodeRulesPacket msg) {
        RemoteEncodeRules.receive(msg.blockFullCategoryBatch, msg.requireOpenEncodingMenu, msg.maxPatternsPerSession);
    }
}
