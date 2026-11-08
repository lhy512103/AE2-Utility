package com.lhy.ae2utility.util;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** A non-owning lookup, even when values themselves strongly reference their keys. */
public final class WeakValueRegistry<K, V> {
    private final Map<K, WeakReference<V>> entries = new WeakHashMap<>();

    public void put(K key, V value) {
        entries.put(key, new WeakReference<>(value));
    }

    public V get(K key) {
        WeakReference<V> reference = entries.get(key);
        return reference == null ? null : reference.get();
    }

    public V find(Predicate<V> predicate) {
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            V value = iterator.next().getValue().get();
            if (value == null) {
                iterator.remove();
            } else if (predicate.test(value)) {
                return value;
            }
        }
        return null;
    }
}
