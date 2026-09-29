package com.lhy.ae2utility.jei;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import com.lhy.ae2utility.client.EaepProviderReuse;
import com.lhy.ae2utility.client.EaepProviderScreenLabel;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.ModNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * JEI Shift 批量上传的客户端节奏控制：一次只发一条，等服务端回执；
 * 若服务端打开了 EAEP 供应器选择界面，则等玩家选完（界面关闭）后再发下一条。
 */
public final class JeiBatchEncodeQueue {
    private static final int SERVER_REPLY_TIMEOUT_TICKS = 200;
    private static final int PROVIDER_SCREEN_OPEN_TIMEOUT_TICKS = 100;
    private static final int NEXT_PACKET_DELAY_TICKS = 2;

    private enum State {
        IDLE, AWAIT_SERVER, AWAIT_SCREEN_OPEN, AWAIT_SCREEN_CLOSE, DELAY
    }

    private static final Deque<EncodePatternPacket> pending = new ArrayDeque<>();
    private static String machineName = "";
    private static State state = State.IDLE;
    private static int timer;

    private JeiBatchEncodeQueue() {
    }

    public static boolean start(List<EncodePatternPacket> packets, String machine) {
        if (packets == null || packets.isEmpty() || isActive()) {
            return false;
        }
        pending.addAll(packets);
        machineName = machine == null ? "" : machine;
        EaepProviderReuse.reset();
        sendNext();
        return true;
    }

    public static boolean isActive() {
        return state != State.IDLE || !pending.isEmpty();
    }

    public static void tick() {
        if (!isActive()) {
            return;
        }
        boolean providerScreenOpen = EaepProviderScreenLabel.isProviderSelectScreen(Minecraft.getInstance().screen);
        if (providerScreenOpen && state != State.AWAIT_SCREEN_CLOSE) {
            state = State.AWAIT_SCREEN_CLOSE;
            return;
        }
        switch (state) {
            case AWAIT_SERVER, AWAIT_SCREEN_OPEN -> {
                if (--timer <= 0) {
                    enterDelay();
                }
            }
            case AWAIT_SCREEN_CLOSE -> {
                if (!providerScreenOpen) {
                    enterDelay();
                }
            }
            case DELAY -> {
                if (--timer <= 0) {
                    state = State.IDLE;
                    sendNext();
                }
            }
            case IDLE -> sendNext();
        }
    }

    public static void onServerStep(boolean awaitingProvider) {
        if (state != State.AWAIT_SERVER) {
            return;
        }
        if (awaitingProvider) {
            state = State.AWAIT_SCREEN_OPEN;
            timer = PROVIDER_SCREEN_OPEN_TIMEOUT_TICKS;
        } else {
            enterDelay();
        }
    }

    public static void cancelFromServer() {
        if (!isActive()) {
            return;
        }
        pending.clear();
        state = State.IDLE;
        EaepProviderReuse.reset();

        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(
                    Component.translatable("message.ae2utility.batch_encode_cancelled")
                            .withStyle(ChatFormatting.YELLOW),
                    false);
        }
    }

    private static void enterDelay() {
        state = State.DELAY;
        timer = NEXT_PACKET_DELAY_TICKS;
    }

    private static void sendNext() {
        EncodePatternPacket next = pending.pollFirst();
        if (next == null) {
            state = State.IDLE;
            EaepProviderReuse.reset();
            return;
        }
        EaepProviderScreenLabel.setCurrent(next.patternName(), machineName);
        ModNetworking.sendToServer(next);
        state = State.AWAIT_SERVER;
        timer = SERVER_REPLY_TIMEOUT_TICKS;
    }
}
