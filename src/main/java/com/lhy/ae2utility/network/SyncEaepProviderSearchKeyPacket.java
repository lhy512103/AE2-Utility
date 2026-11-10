package com.lhy.ae2utility.network;

import com.lhy.ae2utility.Ae2UtilityMod;

import net.minecraft.network.FriendlyByteBuf;
import com.lhy.ae2utility.network.codec.StreamCodec;
import com.lhy.ae2utility.network.codec.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 在打开 EAEP 供应器界面前同步搜索关键字；{@link ExtendedAEPatternUploadUtil} 的供应器列表界面读取的是客户端静态字段，
 * 仅靠服务端 {@link com.lhy.ae2utility.service.EncodePatternService} 预设会导致搜索框为空（尤其在多人游戏）。
 */
public record SyncEaepProviderSearchKeyPacket(boolean craftingPresetOnly, String rawFilter) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncEaepProviderSearchKeyPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "sync_eaep_provider_search"));

    public static final StreamCodec<FriendlyByteBuf, SyncEaepProviderSearchKeyPacket> STREAM_CODEC =
            StreamCodec.ofMember(SyncEaepProviderSearchKeyPacket::write, SyncEaepProviderSearchKeyPacket::decode);

    private static SyncEaepProviderSearchKeyPacket decode(FriendlyByteBuf buffer) {
        return new SyncEaepProviderSearchKeyPacket(buffer.readBoolean(), buffer.readUtf(512));
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(craftingPresetOnly);
        buffer.writeUtf(rawFilter == null ? "" : rawFilter, 512);
    }

    @Override
    public Type<SyncEaepProviderSearchKeyPacket> type() {
        return TYPE;
    }

    public static void handle(SyncEaepProviderSearchKeyPacket payload) {
        ClientPacketDispatch.handle(payload);
    }

}
