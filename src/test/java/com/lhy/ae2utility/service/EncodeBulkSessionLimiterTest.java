package com.lhy.ae2utility.service;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import com.google.common.cache.Cache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EncodeBulkSessionLimiterTest {
    private final UUID player = UUID.randomUUID();

    @AfterEach
    void reset() {
        EncodeBulkSessionLimiter.clearAll();
    }

    @Test
    void failedAttemptsReleaseTheirReservation() {
        for (int i = 0; i < 10; i++) {
            try (var permit = EncodeBulkSessionLimiter.reserve(player, 1, 1)) {
                assertNotNull(permit);
            }
        }
        assertNotNull(EncodeBulkSessionLimiter.reserve(player, 1, 1));
    }

    @Test
    void retainedPatternsCountUntilTheLimit() {
        try (var permit = EncodeBulkSessionLimiter.reserve(player, 1, 1)) {
            assertNotNull(permit);
            permit.commit();
        }
        assertNull(EncodeBulkSessionLimiter.reserve(player, 1, 1));
        assertNull(EncodeBulkSessionLimiter.reserve(player, 1, 1));
    }

    @Test
    void pendingProviderSelectionReservesQuotaAndRefundReleasesItOnce() {
        var pending = EncodeBulkSessionLimiter.reserve(player, 1, 1);
        assertNotNull(pending);
        assertNull(EncodeBulkSessionLimiter.reserve(player, 1, 1));
        pending.commit();
        pending.close();
        assertNull(EncodeBulkSessionLimiter.reserve(player, 1, 1));
        pending.release();
        pending.release();
        assertFalse(pending.isCommitted());
        var next = EncodeBulkSessionLimiter.reserve(player, 1, 1);
        assertNotNull(next);
        assertNull(EncodeBulkSessionLimiter.reserve(player, 1, 1));
        next.close();
    }

    @Test
    void playersAndSessionsHaveIndependentLimits() {
        EncodeBulkSessionLimiter.reserve(player, 1, 1).commit();
        assertNotNull(EncodeBulkSessionLimiter.reserve(player, 2, 1));
        assertNotNull(EncodeBulkSessionLimiter.reserve(UUID.randomUUID(), 1, 1));
    }

    @Test
    void logoutClearsOnlyThatPlayersSessions() {
        UUID other = UUID.randomUUID();
        EncodeBulkSessionLimiter.reserve(player, 1, 1).commit();
        EncodeBulkSessionLimiter.reserve(other, 1, 1).commit();
        EncodeBulkSessionLimiter.clearFor(player);
        assertNotNull(EncodeBulkSessionLimiter.reserve(player, 1, 1));
        assertNull(EncodeBulkSessionLimiter.reserve(other, 1, 1));
        EncodeBulkSessionLimiter.clearAll();
        assertNotNull(EncodeBulkSessionLimiter.reserve(other, 1, 1));
    }

    @Test
    void untrackedAndUnlimitedRequestsDoNotCreateSessions() throws Exception {
        for (int i = 0; i < 10; i++) {
            EncodeBulkSessionLimiter.reserve(player, 0, 1).commit();
            EncodeBulkSessionLimiter.reserve(player, i, -1).commit();
            EncodeBulkSessionLimiter.reserve(player, i, 0).commit();
        }
        assertEquals(0, sessions().size());
    }

    @Test
    void oldSessionStateIsBounded() throws Exception {
        for (int i = 1; i <= 5000; i++) {
            EncodeBulkSessionLimiter.reserve(player, i, 1).commit();
        }
        EncodeBulkSessionLimiter.cleanUp();
        assertTrue(sessions().size() <= 4096);
    }

    @Test
    void concurrentReservationsRespectTheLimit() throws Exception {
        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            attempts.add(() -> {
                try (var permit = EncodeBulkSessionLimiter.reserve(player, 1, 4)) {
                    if (permit == null) {
                        return false;
                    }
                    permit.commit();
                    return true;
                }
            });
        }
        try (var executor = Executors.newFixedThreadPool(8)) {
            int accepted = 0;
            for (var result : executor.invokeAll(attempts)) {
                if (result.get()) {
                    accepted++;
                }
            }
            assertEquals(4, accepted);
        }
    }

    private static Cache<?, ?> sessions() throws Exception {
        Field field = EncodeBulkSessionLimiter.class.getDeclaredField("SESSIONS");
        field.setAccessible(true);
        return (Cache<?, ?>) field.get(null);
    }
}
