package com.lhy.ae2utility.network;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Common-side indirection for S2C packets. A physical client installs its handlers
 * during setup; constructing/registering/encoding packets on a server never
 * resolves a client implementation. Handlers run on the receiving main thread.
 */
public final class ClientPacketDispatch {
    private static final Map<Class<?>, Consumer<Object>> HANDLERS = new ConcurrentHashMap<>();

    private ClientPacketDispatch() {
    }

    public static <T> void register(Class<T> type, Consumer<? super T> handler) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(handler, "handler");
        if (HANDLERS.putIfAbsent(type, payload -> handler.accept(type.cast(payload))) != null) {
            throw new IllegalStateException("Client packet handler already registered: " + type.getName());
        }
    }

    public static void handle(Object payload) {
        if (payload == null) return;
        Consumer<Object> handler = HANDLERS.get(payload.getClass());
        if (handler != null) handler.accept(payload);
    }
}
