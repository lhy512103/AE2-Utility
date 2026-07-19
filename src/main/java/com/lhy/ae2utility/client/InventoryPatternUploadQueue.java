package com.lhy.ae2utility.client;
import com.lhy.ae2utility.compat.ModCapabilities;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.crafting.IPatternDetails;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
import com.lhy.ae2utility.debug.EaepUploadDebugLog;
import com.lhy.ae2utility.debug.InventoryPatternUploadDebug;
import com.lhy.ae2utility.integration.eaep.EaepProviderListRequest;
import com.lhy.ae2utility.init.ModDataComponents;
import com.lhy.ae2utility.network.UploadInventoryPatternToProviderPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

public final class InventoryPatternUploadQueue {
    private static final int NEXT_PACKET_DELAY_TICKS = 4;
    private static final String UNKNOWN_GROUP_KEY = "__ae2utility_unknown__";

    private static final Deque<Integer> PENDING_SLOTS = new ArrayDeque<>();
    private static final Deque<PendingGroup> PENDING_GROUPS = new ArrayDeque<>();
    private static boolean selectingProvider;
    private static int totalPatterns;
    private static int sentPatterns;
    private static int waitTicks;
    private static long providerId;
    private static String providerName = "";
    private static String currentSearchKey = "";
    private static String currentPatternName = "";

    private static Integer awaitingUploadAckSlot;

    private record PendingGroup(List<Integer> slots, String searchKey) {
    }

    private InventoryPatternUploadQueue() {
    }

    public static void handleFallback(List<Integer> remainingSlots) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || remainingSlots.isEmpty()) {
            return;
        }

        if (!ModCapabilities.hasExtendedAePlus()) {
            player.displayClientMessage(Component.literal("需要安装 ExtendedAE+ 才能打开供应器选择界面。").withStyle(ChatFormatting.RED), true);
            return;
        }

        resetAll();
        enqueueGroups(player, remainingSlots);
        if (PENDING_GROUPS.isEmpty()) {
            InventoryPatternUploadDebug.warn("handle_fallback", "no pending groups built from slots={}", remainingSlots);
            return;
        }

        EaepUploadDebugLog.info("handleFallback remainingSlots={} groupCount={}", remainingSlots, PENDING_GROUPS.size());
        startNextSelection();
        InventoryPatternUploadDebug.info("handle_fallback", "provider list requested successfully for remaining slots={}", remainingSlots);
    }

    private static boolean requestProvidersList() {
        boolean ok = EaepProviderListRequest.trySendFromClient();
        InventoryPatternUploadDebug.info(
                "request_providers", ok ? "sent via EaepProviderListRequest" : "EaepProviderListRequest failed");
        return ok;
    }

    public static List<Integer> collectEncodedPatternSlots(LocalPlayer player) {
        List<Integer> slots = new ArrayList<>();
        if (player == null) {
            InventoryPatternUploadDebug.warn("collect_slots", "player is null");
            return slots;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack)) {
                slots.add(slot);
            }
        }
        InventoryPatternUploadDebug.info("collect_slots", "foundEncodedPatternSlots count={} slots={}", slots.size(), slots);
        return slots;
    }

    public static void prepareSelection(List<Integer> slots) {
        resetSelectionState();
        PENDING_SLOTS.addAll(slots);
        totalPatterns = slots.size();
        selectingProvider = !slots.isEmpty();
        InventoryPatternUploadDebug.info("prepare_selection", "slots={} totalPatterns={} selectingProvider={}",
                slots, totalPatterns, selectingProvider);
    }

    public static boolean isSelectingProvider() {
        return selectingProvider;
    }

    public static void startDirectUpload(List<Integer> slots, long chosenProviderId, String chosenProviderName) {
        if (slots == null || slots.isEmpty()) {
            return;
        }
        resetAll();
        PENDING_SLOTS.addAll(slots);
        totalPatterns = slots.size();
        selectingProvider = false;
        providerId = chosenProviderId;
        providerName = chosenProviderName == null ? "" : chosenProviderName;
        InventoryPatternUploadDebug.info("start_direct_upload", "slots={} providerId={} providerName={}",
                slots, chosenProviderId, providerName);
        sendNext();
    }

    public static void cancelSelection() {
        if (selectingProvider) {
            InventoryPatternUploadDebug.info("cancel_selection", "selection cancelled while waiting provider");
            resetSelectionState();
            if (!PENDING_GROUPS.isEmpty()) {
                startNextSelection();
            } else {
                RecipeTreeUploadProgressState.clear();
            }
        }
    }

    public static void beginUploading(long chosenProviderId, String chosenProviderName) {
        if (PENDING_SLOTS.isEmpty()) {
            InventoryPatternUploadDebug.warn("begin_uploading", "queue empty providerId={} providerName={}",
                    chosenProviderId, chosenProviderName);
            resetAll();
            return;
        }
        selectingProvider = false;
        providerId = chosenProviderId;
        providerName = chosenProviderName == null ? "" : chosenProviderName;
        InventoryPatternUploadDebug.info("begin_uploading", "providerId={} providerName={} pendingSlots={}",
                providerId, providerName, PENDING_SLOTS);
        EaepUploadDebugLog.info(
                "beginUploading providerId={} providerName={} pendingSlots={} totalPatterns={}",
                providerId, providerName, PENDING_SLOTS, totalPatterns);

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            String target = providerName.isBlank() ? ("#" + providerId) : providerName;
            player.displayClientMessage(Component.literal("开始批量上传 " + totalPatterns + " 个样板 -> " + target)
                    .withStyle(ChatFormatting.WHITE), true);
        }
        sendNext();
    }

    public static void tick() {
        if (selectingProvider || PENDING_SLOTS.isEmpty()) {
            return;
        }
        if (awaitingUploadAckSlot != null) {
            return;
        }
        if (waitTicks > 0) {
            waitTicks--;
            if (waitTicks == 0) {
                sendNext();
            }
        }
    }

    public static void handleUploadAck(int slotIndex, boolean success) {
        if (awaitingUploadAckSlot == null || awaitingUploadAckSlot.intValue() != slotIndex) {
            return;
        }
        awaitingUploadAckSlot = null;
        LocalPlayer player = Minecraft.getInstance().player;
        if (success) {
            PENDING_SLOTS.pollFirst();
            sentPatterns++;
            waitTicks = PENDING_SLOTS.isEmpty() ? 0 : NEXT_PACKET_DELAY_TICKS;
            if (PENDING_SLOTS.isEmpty()) {
                finish();
            }
        } else {
            onProviderUploadRejected(player);
        }
    }

    /** 取消库存样板供应器批量、供应器选择与会话（含等待 ACK 的槽位）。 */
    public static void cancelAll() {
        resetAll();
    }

    /** 服务端取消数据包下发时：仅清状态，不在此发聊天提示。 */
    public static void cancelAllQuiet() {
        resetAll();
    }

    private static void onProviderUploadRejected(@Nullable LocalPlayer player) {
        if (player == null) {
            resetAll();
            return;
        }
        if (!ModCapabilities.hasExtendedAePlus()) {
            player.displayClientMessage(
                    Component.translatable("message.ae2utility.inventory_upload_provider_failed_no_eaep").withStyle(ChatFormatting.RED),
                    false);
            resetAll();
            return;
        }
        providerId = 0L;
        providerName = "";
        waitTicks = 0;
        selectingProvider = true;
        totalPatterns = PENDING_SLOTS.size();
        if (!requestProvidersList()) {
            player.displayClientMessage(
                    Component.translatable("message.ae2utility.inventory_upload_reopen_providers_failed").withStyle(ChatFormatting.RED),
                    true);
            resetAll();
            return;
        }
        player.displayClientMessage(Component.translatable("message.ae2utility.inventory_upload_provider_full_retry")
                .withStyle(ChatFormatting.GOLD), true);
    }

    private static void sendNext() {
        if (awaitingUploadAckSlot != null) {
            return;
        }
        Integer nextSlot = PENDING_SLOTS.peekFirst();
        if (nextSlot == null) {
            InventoryPatternUploadDebug.info("send_next", "queue empty, finishing");
            finish();
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        currentPatternName = resolvePatternName(player, nextSlot.intValue());
        RecipeTreeUploadProgressState.setCurrent(currentPatternName, providerName.isBlank() ? currentSearchKey : providerName);
        InventoryPatternUploadDebug.info("send_next", "sending playerSlotIndex={} queueSizeIncludingHead={}", nextSlot,
                PENDING_SLOTS.size());
        EaepUploadDebugLog.info("sendNext playerSlot={} providerId={} remainingQueueSize={}", nextSlot, providerId,
                PENDING_SLOTS.size());
        awaitingUploadAckSlot = nextSlot;
        if (!sendInventoryUploadPacket(nextSlot.intValue(), providerId)) {
            awaitingUploadAckSlot = null;
            finishWithError();
        }
    }

    private static boolean sendInventoryUploadPacket(int playerSlotIndex, long chosenProviderId) {
        try {
            PacketDistributor.sendToServer(new UploadInventoryPatternToProviderPacket(playerSlotIndex, chosenProviderId));
            InventoryPatternUploadDebug.info("send_packet", "packetClass={} playerSlotIndex={} providerId={}",
                    UploadInventoryPatternToProviderPacket.class.getName(), playerSlotIndex, chosenProviderId);
            return true;
        } catch (Throwable t) {
            InventoryPatternUploadDebug.warn("send_packet", "failed playerSlotIndex={} providerId={} error={}",
                    playerSlotIndex, chosenProviderId, t.toString());
            return false;
        }
    }

    private static void finish() {
        InventoryPatternUploadDebug.info("finish", "sentPatterns={} providerId={} providerName={}",
                sentPatterns, providerId, providerName);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && sentPatterns > 0) {
            String target = providerName.isBlank() ? ("#" + providerId) : providerName;
            player.displayClientMessage(Component.literal("已提交 " + sentPatterns + " 个样板到 " + target)
                    .withStyle(ChatFormatting.WHITE), false);
        }
        resetSelectionState();
        if (!PENDING_GROUPS.isEmpty()) {
            startNextSelection();
        } else {
            RecipeTreeUploadProgressState.clear();
        }
    }

    private static void finishWithError() {
        InventoryPatternUploadDebug.warn("finish_error", "sentPatterns={} providerId={} providerName={}",
                sentPatterns, providerId, providerName);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal("批量上传失败：未找到 EAEP 上传接口")
                    .withStyle(ChatFormatting.RED), false);
        }
        resetAll();
    }

    private static void enqueueGroups(LocalPlayer player, List<Integer> remainingSlots) {
        Map<String, List<Integer>> groupedSlots = new LinkedHashMap<>();
        for (Integer slot : remainingSlots) {
            if (slot == null || slot < 0 || slot >= player.getInventory().getContainerSize()) {
                continue;
            }
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
                continue;
            }

            String searchKey = readStoredSearchKey(stack);
            if (searchKey.isBlank()) {
                searchKey = deriveFallbackSearchKey(player, stack);
            }
            if (searchKey.isBlank()) {
                searchKey = UNKNOWN_GROUP_KEY;
            }

            groupedSlots.computeIfAbsent(searchKey, ignored -> new ArrayList<>()).add(slot);
        }

        for (Map.Entry<String, List<Integer>> entry : groupedSlots.entrySet()) {
            PENDING_GROUPS.addLast(new PendingGroup(List.copyOf(entry.getValue()), entry.getKey()));
        }

        InventoryPatternUploadDebug.info("enqueue_groups", "groups={}", groupedSlots.keySet());
    }

    private static void startNextSelection() {
        LocalPlayer player = Minecraft.getInstance().player;
        PendingGroup group = PENDING_GROUPS.pollFirst();
        if (player == null || group == null) {
            resetAll();
            return;
        }

        currentSearchKey = UNKNOWN_GROUP_KEY.equals(group.searchKey()) ? "" : group.searchKey();
        currentPatternName = resolvePatternName(player, group.slots().get(0));
        RecipeTreeUploadProgressState.setCurrent(currentPatternName, currentSearchKey);
        if (!presetProviderSearchKey(currentSearchKey)) {
            InventoryPatternUploadDebug.warn("start_next_selection", "failed to preset search key={}", currentSearchKey);
        }
        if (!requestProvidersList()) {
            InventoryPatternUploadDebug.warn("start_next_selection", "request providers list failed searchKey={}", currentSearchKey);
            player.displayClientMessage(Component.literal("批量上传失败：无法打开供应器列表").withStyle(ChatFormatting.RED), true);
            resetAll();
            return;
        }
        prepareSelection(group.slots());
        if (!currentSearchKey.isBlank()) {
            player.displayClientMessage(Component.literal("已按机器关键词筛选：" + currentSearchKey).withStyle(ChatFormatting.GRAY), true);
        }
        EaepUploadDebugLog.info(
                "startNextSelection searchKey={} slots={} selectingProvider={} totalPatterns={}",
                currentSearchKey, group.slots(), selectingProvider, totalPatterns);
        InventoryPatternUploadDebug.info("start_next_selection", "searchKey={} slots={}", currentSearchKey, group.slots());
    }

    private static boolean presetProviderSearchKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return true;
        }
        String resolvedKey = com.lhy.ae2utility.integration.eaep.EaepReflection.resolveSearchKeyAlias(rawKey);
        if (resolvedKey == null) {
            InventoryPatternUploadDebug.warn("preset_search_key", "resolveAlias failed rawKey={}", rawKey);
            return false;
        }
        return com.lhy.ae2utility.integration.eaep.EaepReflection.setLastProviderSearchKey(resolvedKey);
    }

    private static String readStoredSearchKey(ItemStack stack) {
        String stored = stack.getOrDefault(ModDataComponents.PATTERN_PROVIDER_SEARCH_KEY.get(), "");
        return stored == null ? "" : stored.trim();
    }

    private static String deriveFallbackSearchKey(LocalPlayer player, ItemStack stack) {
        if (player == null || player.level() == null || stack.isEmpty()) {
            return "";
        }
        try {
            IPatternDetails details = PatternDetailsHelper.decodePattern(stack, player.level());
            if (details instanceof AECraftingPattern || details instanceof AESmithingTablePattern
                    || details instanceof AEStonecuttingPattern) {
                return "crafting";
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static String resolvePatternName(LocalPlayer player, int slotIndex) {
        if (player == null || slotIndex < 0 || slotIndex >= player.getInventory().getContainerSize()) {
            return "-";
        }

        ItemStack stack = player.getInventory().getItem(slotIndex);
        if (stack.isEmpty()) {
            return "-";
        }

        try {
            if (player.level() != null && PatternDetailsHelper.isEncodedPattern(stack)) {
                IPatternDetails details = PatternDetailsHelper.decodePattern(stack, player.level());
                if (details != null && !details.getOutputs().isEmpty() && details.getOutputs().get(0) != null
                        && details.getOutputs().get(0).what() != null) {
                    return details.getOutputs().get(0).what().getDisplayName().getString();
                }
            }
        } catch (Throwable ignored) {
        }

        return stack.getHoverName().getString();
    }

    private static void resetSelectionState() {
        InventoryPatternUploadDebug.info("reset", "clearingState pending={} selectingProvider={} totalPatterns={} sentPatterns={} waitTicks={} providerId={} providerName={}",
                PENDING_SLOTS, selectingProvider, totalPatterns, sentPatterns, waitTicks, providerId, providerName);
        awaitingUploadAckSlot = null;
        PENDING_SLOTS.clear();
        selectingProvider = false;
        totalPatterns = 0;
        sentPatterns = 0;
        waitTicks = 0;
        providerId = 0L;
        providerName = "";
        currentPatternName = "";
    }

    private static void resetAll() {
        resetSelectionState();
        PENDING_GROUPS.clear();
        currentSearchKey = "";
        RecipeTreeUploadProgressState.clear();
        EaepPendingProviderSearch.forgetResolvedFilterReuse();
    }
}
