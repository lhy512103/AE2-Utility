package com.lhy.ae2utility.client.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ClientRequestCacheTest {
    @Test
    void rejectsRepliesAfterReplacementOrClearEvenForSameKey() {
        AtomicLong time = new AtomicLong(10);
        var cache = new ClientRequestCache<String, String>(4, 100, 20, time::get);
        cache.beginRequest("menu-2:recipe", 11);
        cache.beginRequest("menu-2:recipe", 12);
        assertFalse(cache.complete("menu-2:recipe", 11, "old"));
        assertTrue(cache.complete("menu-2:recipe", 12, "new"));
        assertEquals("new", cache.get("menu-2:recipe"));
        cache.beginRequest("menu-2:recipe", 13);
        cache.clear();
        assertFalse(cache.complete("menu-2:recipe", 13, "after-clear"));
    }

    @Test
    void expiresRetriesAndBoundsLruEntries() {
        AtomicLong time = new AtomicLong(100);
        var cache = new ClientRequestCache<String, String>(2, 50, 20, time::get);
        cache.beginRequest("a", 21);
        cache.beginRequest("b", 22);
        assertTrue(cache.complete("a", 21, "A"));
        assertEquals("A", cache.get("a"));
        cache.beginRequest("c", 23);
        assertNull(cache.get("b"));
        assertEquals(2, cache.size());
        assertFalse(cache.shouldRequest("a"));
        time.addAndGet(51);
        assertNull(cache.get("a"));
        assertTrue(cache.shouldRequest("a"));
    }

    @Test
    void lifecycleGenerationUsesIdentityAndAdvancesOnExplicitReset() {
        var generation = new ClientCacheGeneration();
        Object world = new Object();
        Object player = new Object();
        Object menu = new Object();
        assertTrue(generation.observe(world, player, menu));
        long first = generation.generation();
        assertFalse(generation.observe(world, player, menu));
        assertTrue(generation.observe(world, player, new Object()));
        assertTrue(generation.generation() > first);
        generation.invalidate();
        assertTrue(generation.generation() > first + 1);
    }
}
