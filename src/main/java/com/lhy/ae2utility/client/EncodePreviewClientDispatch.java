package com.lhy.ae2utility.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.lhy.ae2utility.jei.EncodePatternButtonController;
import com.lhy.ae2utility.network.EncodePreviewResultPacket;

public final class EncodePreviewClientDispatch {
    private static final List<Consumer<EncodePreviewResultPacket>> LISTENERS = new CopyOnWriteArrayList<>();

    private EncodePreviewClientDispatch() {
    }

    public static void addListener(Consumer<EncodePreviewResultPacket> listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    public static void handle(EncodePreviewResultPacket payload) {
        EncodePatternButtonController.handlePreviewResult(payload);
        for (Consumer<EncodePreviewResultPacket> listener : LISTENERS) {
            listener.accept(payload);
        }
    }
}
