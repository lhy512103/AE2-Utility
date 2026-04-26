package com.lhy.ae2utility.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.lhy.ae2utility.jei.JeiBookmarkUtil;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.RecipeTreeUploadResultPacket;

import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RecipeTreeUploadQueue {
    private static final int NEXT_PACKET_DELAY_TICKS = 8;

    private static final Deque<EncodePatternPacket> PENDING = new ArrayDeque<>();
    private static final Deque<EncodePatternPacket> IN_FLIGHT = new ArrayDeque<>();
    private static final List<String> SUCCEEDED = new ArrayList<>();
    private static final List<String> FAILED = new ArrayList<>();
    private static int expectedResults;
    private static int receivedResults;
    private static int waitTicks;
    private static boolean waitingForProviderScreen;

    private RecipeTreeUploadQueue() {
    }

    public static void start(List<EncodePatternPacket> packets) {
        PENDING.clear();
        IN_FLIGHT.clear();
        SUCCEEDED.clear();
        FAILED.clear();
        PENDING.addAll(packets);
        expectedResults = packets.size();
        receivedResults = 0;
        waitTicks = 0;
        waitingForProviderScreen = false;
        sendNext();
    }

    public static void tick() {
        if (PENDING.isEmpty() && !waitingForProviderScreen && waitTicks <= 0) {
            return;
        }

        boolean providerScreenOpen = isEaepProviderSelectScreen(Minecraft.getInstance().screen);
        if (providerScreenOpen) {
            waitingForProviderScreen = true;
            return;
        }

        if (waitingForProviderScreen) {
            waitingForProviderScreen = false;
            waitTicks = Math.max(waitTicks, 4);
            return;
        }

        if (waitTicks > 0) {
            waitTicks--;
            if (waitTicks == 0) {
                sendNext();
            }
        }
    }

    public static void handleResult(RecipeTreeUploadResultPacket packet) {
        EncodePatternPacket sentPacket = IN_FLIGHT.pollFirst();
        if (packet.patternName() != null && !packet.patternName().isBlank()) {
            (packet.uploaded() ? SUCCEEDED : FAILED).add(packet.patternName());
        }
        if (!packet.uploaded() && sentPacket != null) {
            JeiBookmarkUtil.bookmarkRecipe(sentPacket.recipeId(),
                    sentPacket.outputs().isEmpty() ? null : sentPacket.outputs().getFirst());
        }
        receivedResults++;
        showSummaryIfComplete();
    }

    private static void sendNext() {
        EncodePatternPacket next = PENDING.pollFirst();
        if (next == null) {
            waitTicks = 0;
            showSummaryIfComplete();
            return;
        }
        RecipeTreeUploadProgressState.setCurrent(next);
        IN_FLIGHT.addLast(next);
        PacketDistributor.sendToServer(next);
        waitTicks = NEXT_PACKET_DELAY_TICKS;
    }

    private static boolean isEaepProviderSelectScreen(Screen screen) {
        return screen != null && "com.extendedae_plus.client.screen.ProviderSelectScreen".equals(screen.getClass().getName());
    }

    private static void showSummaryIfComplete() {
        if (expectedResults <= 0 || receivedResults < expectedResults || !PENDING.isEmpty() || waitingForProviderScreen || waitTicks > 0) {
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.ae2utility.recipe_tree_upload_summary",
                    joinNames(SUCCEEDED), joinNames(FAILED)), false);
        }
        expectedResults = 0;
        receivedResults = 0;
        IN_FLIGHT.clear();
        SUCCEEDED.clear();
        FAILED.clear();
        RecipeTreeUploadProgressState.clear();
    }

    private static String joinNames(List<String> names) {
        return names.isEmpty() ? "-" : String.join(",", names);
    }
}
