package com.lhy.ae2utility.service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.world.inventory.AbstractContainerMenu;

class WirelessTerminalContextResolverTest {
    @AfterEach
    void reset() {
        WirelessTerminalContextResolver.clearAll();
    }

    @Test
    void tickRemovesExpiredEntriesWithoutAnotherLookup() throws Exception {
        UUID expired = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        cache().put(expired, entry(System.currentTimeMillis() - 1));
        cache().put(fresh, entry(System.currentTimeMillis() + 60_000));

        WirelessTerminalContextResolver.cleanUp();

        assertFalse(cache().containsKey(expired));
        assertTrue(cache().containsKey(fresh));
    }

    @Test
    void logoutRemovesOnlyThePlayersEntry() throws Exception {
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        cache().put(player, entry(Long.MAX_VALUE));
        cache().put(other, entry(Long.MAX_VALUE));
        WirelessTerminalContextResolver.clearFor(player);
        assertFalse(cache().containsKey(player));
        assertTrue(cache().containsKey(other));
    }

    @Test
    void serverStopClearsAllEntries() throws Exception {
        cache().put(UUID.randomUUID(), entry(Long.MAX_VALUE));
        WirelessTerminalContextResolver.clearAll();
        assertTrue(cache().isEmpty());
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> cache() throws Exception {
        Field field = WirelessTerminalContextResolver.class.getDeclaredField("CACHE");
        field.setAccessible(true);
        return (Map<UUID, Object>) field.get(null);
    }

    private static Object entry(long expiresAt) throws Exception {
        Class<?> type = Class.forName(WirelessTerminalContextResolver.class.getName() + "$CacheEntry");
        var constructor = type.getDeclaredConstructor(AbstractContainerMenu.class,
                WirelessTerminalContextResolver.Resolution.class, long.class);
        constructor.setAccessible(true);
        var resolution = new WirelessTerminalContextResolver.Resolution(
                WirelessTerminalContextResolver.Status.DISCONNECTED, null, -1, "test");
        return constructor.newInstance(null, resolution, expiresAt);
    }
}
