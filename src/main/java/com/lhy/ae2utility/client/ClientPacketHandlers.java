package com.lhy.ae2utility.client;

import com.lhy.ae2utility.compat.ModCapabilities;
import com.lhy.ae2utility.debug.EaepUploadDebugLog;
import com.lhy.ae2utility.integration.eaep.EaepReflection;
import com.lhy.ae2utility.jei.CraftableStateCache;
import com.lhy.ae2utility.jei.MachineRecipeStateCache;
import com.lhy.ae2utility.network.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Installed only by physical-client setup; common packet classes contain data/codecs only. */
public final class ClientPacketHandlers {
    private ClientPacketHandlers() {
    }

    public static void register() {
        ClientPacketDispatch.register(EncodePreviewResultPacket.class, ClientPacketHandlers::handle);
        ClientPacketDispatch.register(SyncAe2UtilityEncodeRulesPacket.class, ClientPacketHandlers::handle);
        ClientPacketDispatch.register(SequentialUploadResultPacket.class, ClientPacketHandlers::handle);
        ClientPacketDispatch.register(InventoryProviderSelectionPreparedPacket.class, ClientPacketHandlers::handle);
        ClientPacketDispatch.register(InventoryProviderUploadAckPacket.class, ClientPacketHandlers::handle);
        ClientPacketDispatch.register(CancelClientUploadQueuesPacket.class, ClientPacketHandlers::handle);
        ClientPacketDispatch.register(SyncEaepProviderSearchKeyPacket.class, ClientPacketHandlers::handle);
        ClientPacketDispatch.register(MachineRecipeStatePacket.class, MachineRecipeStateCache::handle);
        ClientPacketDispatch.register(CraftableStatePacket.class, CraftableStateCache::handle);
        ClientPacketDispatch.register(InvalidateCraftableCachePacket.class,
                packet -> CraftableStateCache.invalidateKeys(packet.keys()));
        ClientPacketDispatch.register(FallbackToProviderSelectionPacket.class,
                packet -> InventoryPatternUploadQueue.handleFallback(packet.slots()));
    }

    private static void handle(EncodePreviewResultPacket payload) {
        EncodePreviewClientDispatch.handle(payload);
    }

    private static void handle(SyncAe2UtilityEncodeRulesPacket p) {
        RemoteEncodeRules.receiveFromServer(p.blockJeFullCategoryBatchEncode(), p.requireOpenPatternEncodingMenuForJe(),
                p.jeiBulkEncodeMaxPatternsPerSession());
    }

    private static void handle(SequentialUploadResultPacket packet) {
        SequentialUploadQueue.handleResult(packet);
    }

    private static void handle(InventoryProviderSelectionPreparedPacket packet) {
        InventoryPatternUploadQueue.handleProviderSelectionPrepared(packet.playerSlotIndex(), packet.success());
    }

    private static void handle(InventoryProviderUploadAckPacket packet) {
        InventoryPatternUploadQueue.handleUploadAck(packet.slotIndex(), packet.success());
    }

    private static void handle(CancelClientUploadQueuesPacket payload) {
        SequentialUploadQueue.cancelAllQuiet();
        InventoryPatternUploadQueue.cancelAllQuiet();
        var player = Minecraft.getInstance().player;
        if (payload.stoppedByOperatorForEveryone() && player != null) {
            player.displayClientMessage(
                    Component.translatable("message.ae2utility.uploads_stopped_by_admin").withStyle(ChatFormatting.GOLD),
                    false);
        }
    }

    private static void handle(SyncEaepProviderSearchKeyPacket payload) {
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
        if (s == null) return "null";
        if (s.length() <= 48) return s.replace('\n', ' ');
        return s.substring(0, 45).replace('\n', ' ') + "...";
    }
}
