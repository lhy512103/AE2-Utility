package com.lhy.ae2utility.client;
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
import com.lhy.ae2utility.debug.InventoryPatternUploadDebug;
import com.lhy.ae2utility.init.ModDataComponents;
import com.lhy.ae2utility.network.UploadInventoryPatternToProviderPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class InventoryPatternUploadQueue {
    private static final int NEXT_PACKET_DELAY_TICKS = 4;
    private static final String REQUEST_PACKET_CLASS = "com.extendedae_plus.network.RequestProvidersListC2SPacket";
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

    private record PendingGroup(List<Integer> slots, String searchKey) {
    }

    private InventoryPatternUploadQueue() {
    }

    public static void handleFallback(List<Integer> remainingSlots) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || remainingSlots.isEmpty()) {
            return;
        }

        if (!net.neoforged.fml.ModList.get().isLoaded("extendedae_plus")) {
            player.displayClientMessage(Component.literal("需要安装 ExtendedAE+ 才能打开供应器选择界面。").withStyle(ChatFormatting.RED), true);
            return;
        }

        resetAll();
        enqueueGroups(player, remainingSlots);
        if (PENDING_GROUPS.isEmpty()) {
            InventoryPatternUploadDebug.warn("handle_fallback", "no pending groups built from slots={}", remainingSlots);
            return;
        }

        startNextSelection();
        InventoryPatternUploadDebug.info("handle_fallback", "provider list requested successfully for remaining slots={}", remainingSlots);
    }

    private static boolean requestProvidersList() {
        try {
            Class<?> packetClass = Class.forName(REQUEST_PACKET_CLASS);
            java.lang.reflect.Field instanceField = packetClass.getDeclaredField("INSTANCE");
            Object packet = instanceField.get(null);
            PacketDistributor.sendToServer((net.minecraft.network.protocol.common.custom.CustomPacketPayload) packet);
            InventoryPatternUploadDebug.info("request_providers", "packetClass={} sent", packetClass.getName());
            return true;
        } catch (Throwable t) {
            InventoryPatternUploadDebug.warn("request_providers", "failed error={}", t.toString());
            return false;
        }
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
        if (waitTicks > 0) {
            waitTicks--;
            if (waitTicks == 0) {
                sendNext();
            }
        }
    }

    private static void sendNext() {
        Integer nextSlot = PENDING_SLOTS.pollFirst();
        if (nextSlot == null) {
            InventoryPatternUploadDebug.info("send_next", "queue empty, finishing");
            finish();
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        currentPatternName = resolvePatternName(player, nextSlot.intValue());
        RecipeTreeUploadProgressState.setCurrent(currentPatternName, providerName.isBlank() ? currentSearchKey : providerName);
        InventoryPatternUploadDebug.info("send_next", "sending playerSlotIndex={} remainingAfterPoll={}", nextSlot, PENDING_SLOTS);
        if (!sendInventoryUploadPacket(nextSlot.intValue(), providerId)) {
            finishWithError();
            return;
        }
        sentPatterns++;
        waitTicks = PENDING_SLOTS.isEmpty() ? 0 : NEXT_PACKET_DELAY_TICKS;
        if (waitTicks == 0) {
            finish();
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
        InventoryPatternUploadDebug.info("start_next_selection", "searchKey={} slots={}", currentSearchKey, group.slots());
    }

    private static boolean presetProviderSearchKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return true;
        }
        try {
            Class<?> utilClass = Class.forName("com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil");
            java.lang.reflect.Method setKeyMethod = utilClass.getMethod("setLastProviderSearchKey", String.class);
            java.lang.reflect.Method resolveAliasMethod = utilClass.getMethod("resolveSearchKeyAlias", String.class);
            String resolvedKey = (String) resolveAliasMethod.invoke(null, rawKey);
            setKeyMethod.invoke(null, resolvedKey);
            return true;
        } catch (Throwable t) {
            InventoryPatternUploadDebug.warn("preset_search_key", "failed rawKey={} error={}", rawKey, t.toString());
            return false;
        }
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
    }
}
