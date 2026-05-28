package com.lhy.ae2utility.network;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.client.RemoteEncodeRules;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 登录或重载服务端配置时将编码相关策略下发到客户端，用于在点击批量按钮时预检提示（减少无效发包与刷屏）。
 */
public record SyncAe2UtilityEncodeRulesPacket(boolean blockJeFullCategoryBatchEncode, boolean requireOpenPatternEncodingMenuForJe,
        int jeiBulkEncodeMaxPatternsPerSession) implements CustomPacketPayload {

    public static final Type<SyncAe2UtilityEncodeRulesPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "sync_encode_rules"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncAe2UtilityEncodeRulesPacket> STREAM_CODEC =
            StreamCodec.of((buf, p) -> {
                buf.writeBoolean(p.blockJeFullCategoryBatchEncode());
                buf.writeBoolean(p.requireOpenPatternEncodingMenuForJe());
                buf.writeVarInt(p.jeiBulkEncodeMaxPatternsPerSession());
            }, buf -> new SyncAe2UtilityEncodeRulesPacket(buf.readBoolean(), buf.readBoolean(), buf.readVarInt()));

    @Override
    public Type<SyncAe2UtilityEncodeRulesPacket> type() {
        return TYPE;
    }

    public static void handle(SyncAe2UtilityEncodeRulesPacket p) {
        RemoteEncodeRules.receiveFromServer(p.blockJeFullCategoryBatchEncode(), p.requireOpenPatternEncodingMenuForJe(),
                p.jeiBulkEncodeMaxPatternsPerSession());
    }
}
