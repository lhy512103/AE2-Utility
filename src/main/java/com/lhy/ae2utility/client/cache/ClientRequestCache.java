package com.lhy.ae2utility.client.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** Bounded access-order cache. All operations run on the client thread; clock is monotonic milliseconds.
 * Keys include the request's semantic identity; lifecycle owners clear on scope changes.
 * A nonce is never reused, even after clear, so a late reply cannot populate a new session.
 */
public final class ClientRequestCache<K, V> {
    private static final AtomicLong NEXT_NONCE = new AtomicLong(1);
    private final Map<K, Entry<V>> entries = new LinkedHashMap<>(16, .75f, true);
    private final int capacity;
    private final long ttl;
    private final long retryInterval;
    private final LongSupplier clock;

    public ClientRequestCache(int capacity, long ttl, long retryInterval, LongSupplier clock) {
        if (capacity <= 0 || ttl <= 0 || retryInterval <= 0) throw new IllegalArgumentException();
        this.capacity = capacity;
        this.ttl = ttl;
        this.retryInterval = retryInterval;
        this.clock = clock;
    }

    public static long nextNonce() { return NEXT_NONCE.getAndIncrement(); }
    public static long monotonicMillis() { return System.nanoTime() / 1_000_000L; }

    public V get(K key) {
        Entry<V> entry = entries.get(key);
        return entry != null && clock.getAsLong() - entry.receivedAt < ttl ? entry.value : null;
    }

    public boolean shouldRequest(K key) {
        Entry<V> entry = entries.get(key);
        long now = clock.getAsLong();
        return entry == null || ((entry.value == null || now - entry.receivedAt >= ttl)
                && now - entry.requestedAt >= retryInterval);
    }

    public void beginRequest(K key, long nonce) {
        entries.put(key, new Entry<>(null, 0, clock.getAsLong(), nonce));
        while (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
    }

    public boolean complete(K key, long nonce, V value) {
        Entry<V> entry = entries.get(key);
        if (entry == null || entry.nonce != nonce || nonce == 0) return false;
        entries.put(key, new Entry<>(value, clock.getAsLong(), entry.requestedAt, 0));
        return true;
    }

    public void remove(K key) { entries.remove(key); }
    public void clear() { entries.clear(); }
    public int size() { return entries.size(); }
    private record Entry<V>(V value, long receivedAt, long requestedAt, long nonce) {}
}
