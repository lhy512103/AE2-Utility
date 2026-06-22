package com.lhy.ae2utility.jei;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.ModNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Minimal client-side pacing for JEI Shift batch uploads. It avoids sending a
 * whole batch while EAEP's provider-selection screen is still resolving one
 * pending pattern.
 */
public final class JeiBatchEncodeQueue {
    private static final int NEXT_PACKET_DELAY_TICKS = 4;
    private static final String EAEP_PROVIDER_SELECT_SCREEN = "com.extendedae_plus.client.screen.ProviderSelectScreen";

    private static final Deque<EncodePatternPacket> pending = new ArrayDeque<>();
    private static int waitTicks;
    private static boolean waitingForProviderScreen;

    private JeiBatchEncodeQueue() {
    }

    public static boolean start(List<EncodePatternPacket> packets) {
        if (packets == null || packets.isEmpty()) {
            return false;
        }
        if (isActive()) {
            return false;
        }
        pending.addAll(packets);
        waitTicks = 0;
        waitingForProviderScreen = false;
        sendNext();
        return true;
    }

    public static void tick() {
        if (!isActive()) {
            return;
        }

        Screen screen = Minecraft.getInstance().screen;
        boolean providerScreenOpen = screen != null && EAEP_PROVIDER_SELECT_SCREEN.equals(screen.getClass().getName());
        if (providerScreenOpen) {
            waitingForProviderScreen = true;
            return;
        }

        if (waitingForProviderScreen) {
            waitingForProviderScreen = false;
            waitTicks = Math.max(waitTicks, NEXT_PACKET_DELAY_TICKS);
            return;
        }

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        sendNext();
    }

    public static void cancelFromServer() {
        if (!isActive()) {
            return;
        }
        pending.clear();
        waitTicks = 0;
        waitingForProviderScreen = false;

        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("message.ae2utility.batch_encode_cancelled")
                            .withStyle(ChatFormatting.YELLOW),
                    false);
        }
    }

    private static boolean isActive() {
        return !pending.isEmpty() || waitTicks > 0 || waitingForProviderScreen;
    }

    private static void sendNext() {
        EncodePatternPacket next = pending.pollFirst();
        if (next == null) {
            waitTicks = 0;
            waitingForProviderScreen = false;
            return;
        }
        ModNetworking.sendToServer(next);
        waitTicks = NEXT_PACKET_DELAY_TICKS;
    }
}
