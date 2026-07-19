package com.lhy.ae2utility.network;

import com.lhy.ae2utility.compat.ModCapabilities;
import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.client.EaepPendingProviderSearch;
import com.lhy.ae2utility.debug.EaepUploadDebugLog;
import com.lhy.ae2utility.integration.eaep.EaepReflection;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 在打开 EAEP 供应器界面前同步搜索关键字；{@link ExtendedAEPatternUploadUtil} 的供应器列表界面读取的是客户端静态字段，
 * 仅靠服务端 {@link com.lhy.ae2utility.service.EncodePatternService} 预设会导致搜索框为空（尤其在多人游戏）。
 */
public record SyncEaepProviderSearchKeyPacket(boolean craftingPresetOnly, String rawFilter) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncEaepProviderSearchKeyPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "sync_eaep_provider_search"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncEaepProviderSearchKeyPacket> STREAM_CODEC =
            StreamCodec.ofMember(SyncEaepProviderSearchKeyPacket::write, SyncEaepProviderSearchKeyPacket::decode);

    private static SyncEaepProviderSearchKeyPacket decode(RegistryFriendlyByteBuf buffer) {
        return new SyncEaepProviderSearchKeyPacket(buffer.readBoolean(), buffer.readUtf(512));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(craftingPresetOnly);
        buffer.writeUtf(rawFilter == null ? "" : rawFilter, 512);
    }

    @Override
    public Type<SyncEaepProviderSearchKeyPacket> type() {
        return TYPE;
    }

    public static void handle(SyncEaepProviderSearchKeyPacket payload) {
        if (!ModCapabilities.hasExtendedAePlus()) {
            return;
        }
        var mc = net.minecraft.client.Minecraft.getInstance();
        String topUi = mc != null && mc.screen != null ? mc.screen.getClass().getSimpleName() : "null";

        try {
            EaepUploadDebugLog.info(
                    "handle enter topScreenSimple={} craftingPresetOnly={} rawLen={} rawSnippet={}",
                    topUi,
                    payload.craftingPresetOnly(), payload.rawFilter() != null ? payload.rawFilter().length() : -1,
                    snippet(payload.rawFilter()));

            String resolvedForUi = "";

            if (payload.craftingPresetOnly()) {
                String dk = EaepReflection.defaultCraftingSearchKey();
                if (dk != null && !dk.isBlank()) {
                    String resolved = EaepReflection.resolveSearchKeyAlias(dk);
                    resolvedForUi = resolved != null ? resolved : "";
                }
            } else {
                String raw = payload.rawFilter();
                if (raw != null && !raw.isBlank()) {
                    String resolved = EaepReflection.resolveSearchKeyAlias(raw);
                    resolvedForUi = resolved != null ? resolved : "";
                }
            }

            // 与 InventoryPatternUploadQueue.presetProviderSearchKey 一致：只写供应器搜索键；非空时再 pending，
            // 避免后续一次空同步覆盖尚未应用到 EditBox 的关键字。
            if (!resolvedForUi.isBlank()) {
                EaepReflection.setLastProviderSearchKey(resolvedForUi);
                EaepPendingProviderSearch.offerResolvedFilter(resolvedForUi);
                EaepPendingProviderSearch.applyResolvedFilterToOpenScreen(resolvedForUi);
            }
            EaepUploadDebugLog.info(
                    "client SyncEaep craftingPresetOnly={} rawLen={} resolvedSnippet={} offeredToPending={}",
                    payload.craftingPresetOnly(), payload.rawFilter() != null ? payload.rawFilter().length() : -1,
                    snippet(resolvedForUi),
                    !resolvedForUi.isBlank());
        } catch (Throwable t) {
            EaepUploadDebugLog.error("client SyncEaepProviderSearchKeyPacket handle failed", t);
        }
    }

    private static String snippet(String s) {
        if (s == null) {
            return "null";
        }
        if (s.length() <= 48) {
            return s.replace('\n', ' ');
        }
        return s.substring(0, 45).replace('\n', ' ') + "...";
    }
}
