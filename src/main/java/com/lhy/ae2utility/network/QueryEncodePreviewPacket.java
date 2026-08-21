package com.lhy.ae2utility.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.lhy.ae2utility.Ae2UtilityMod;

/**
 * 悬停 JEI 编码箭头时，请服务端按真正编码路径算出样板输入并回传。
 */
public record QueryEncodePreviewPacket(int requestId, EncodePatternPacket recipe) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<QueryEncodePreviewPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "query_encode_preview"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QueryEncodePreviewPacket> STREAM_CODEC =
            StreamCodec.ofMember(QueryEncodePreviewPacket::write, QueryEncodePreviewPacket::decode);

    private static QueryEncodePreviewPacket decode(RegistryFriendlyByteBuf buffer) {
        int requestId = buffer.readVarInt();
        EncodePatternPacket recipe = EncodePatternPacket.STREAM_CODEC.decode(buffer);
        return new QueryEncodePreviewPacket(requestId, recipe);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(requestId);
        EncodePatternPacket.STREAM_CODEC.encode(buffer, recipe);
    }

    @Override
    public Type<QueryEncodePreviewPacket> type() {
        return TYPE;
    }
}