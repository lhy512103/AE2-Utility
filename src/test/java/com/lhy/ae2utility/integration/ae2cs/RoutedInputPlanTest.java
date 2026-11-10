package com.lhy.ae2utility.integration.ae2cs;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class RoutedInputPlanTest {
    private record Key(String item, String nbt) {
    }

    private static final Key ENCODED = new Key("tool", "encoded");
    private static final Key RED = new Key("tool", "red");
    private static final Key BLUE = new Key("tool", "blue");

    private static RoutedInputPlan.Demand<Key> demand(Key key, long amount, int slot) {
        return new RoutedInputPlan.Demand<>(key, amount, slot);
    }

    private static List<RoutedInputPlan.Resolved<Key>> resolve(List<RoutedInputPlan.Demand<Key>> demands,
            List<Map<Key, Long>> holders) {
        return RoutedInputPlan.resolve(demands, holders, (a, b) -> a.item().equals(b.item())).orElseThrow();
    }

    @Test
    void combinesSameActualKeyAcrossHoldersWithoutDoubleSpendingRepeatedDemands() {
        Map<Key, Long> first = new LinkedHashMap<>(Map.of(RED, 3L));
        Map<Key, Long> second = new LinkedHashMap<>(Map.of(RED, 4L));
        var demands = List.of(demand(ENCODED, 4, 0), demand(ENCODED, 3, 5));
        var result = resolve(demands, List.of(first, second));
        assertEquals(List.of(new RoutedInputPlan.Resolved<>(RED, 4L, 0),
                new RoutedInputPlan.Resolved<>(RED, 3L, 5)), result);
        assertEquals(Map.of(RED, 3L), first);
        assertEquals(Map.of(RED, 4L), second);
        assertTrue(RoutedInputPlan.resolve(List.of(demand(ENCODED, 4, 0), demand(ENCODED, 4, 5)),
                List.of(first, second), (a, b) -> true).isEmpty());
        assertEquals(Map.of(RED, 3L), first);
        assertEquals(Map.of(RED, 4L), second);
    }

    @Test
    void splitNbtVariantsKeepOriginalSparseRoutingIncludingHoles() {
        Map<Key, Long> holder = new LinkedHashMap<>();
        holder.put(RED, 2L);
        holder.put(BLUE, 3L);
        var result = resolve(List.of(demand(ENCODED, 4, 2), demand(ENCODED, 1, 8)), List.of(holder));
        assertEquals(List.of(new RoutedInputPlan.Resolved<>(RED, 2, 2),
                new RoutedInputPlan.Resolved<>(BLUE, 2, 2), new RoutedInputPlan.Resolved<>(BLUE, 1, 8)), result);
        var attempt = new RoutingAttempt<String, Object>();
        attempt.setSourceIndices(result.stream().map(RoutedInputPlan.Resolved::sourceIndex).toList());
        assertEquals(List.of(2, 2, 8), List.of(attempt.sourceIndex(0), attempt.sourceIndex(1), attempt.sourceIndex(2)));
        assertThrows(UnsupportedOperationException.class, () -> result.clear());
    }

    @Test
    void reservesAllExactInputsBeforeBorrowingAlternatives() {
        var result = RoutedInputPlan.resolve(List.of(demand(ENCODED, 2, 0), demand(RED, 2, 1)),
                List.of(Map.of(RED, 2L, BLUE, 2L)),
                (expected, actual) -> expected.equals(ENCODED) && actual.item().equals("tool")).orElseThrow();
        assertEquals(List.of(new RoutedInputPlan.Resolved<>(BLUE, 2, 0),
                new RoutedInputPlan.Resolved<>(RED, 2, 1)), result);
    }

    @Test
    void strictModeRejectsNbtMismatchAndRollbackLeavesAllInputsForCpu() {
        Map<Key, Long> holder = new LinkedHashMap<>(Map.of(RED, 4L));
        var before = Map.copyOf(holder);
        assertTrue(RoutedInputPlan.resolve(List.of(demand(ENCODED, 4, 0)), List.of(holder), (a, b) -> false).isEmpty());
        assertEquals(before, holder);
        assertTrue(RoutedInputPlan.resolve(List.of(demand(RED, 5, 0)), List.of(holder), (a, b) -> true).isEmpty());
        assertEquals(before, holder);
        assertTrue(RoutedInputPlan.resolve(List.of(demand(RED, 3, 0)), List.of(holder), (a, b) -> true).isEmpty(),
                "CPU transfers the entire holder on success; surplus cannot silently disappear");
        assertEquals(before, holder);
    }

    @Test
    void rejectsOverflowAndInvalidBudgetsWithoutMutatingCallers() {
        assertTrue(RoutedInputPlan.resolve(List.of(demand(RED, 1, 0)),
                List.of(Map.of(RED, Long.MAX_VALUE), Map.of(RED, 1L)), (a, b) -> true).isEmpty());
        assertTrue(RoutedInputPlan.resolve(List.of(demand(RED, 1, 0)),
                List.of(Map.of(RED, -1L)), (a, b) -> true).isEmpty());
    }

    @Test
    void simulationModulationRemoteAndReloadedOverflowRetainIdenticalActualVariants() {
        var plan = resolve(List.of(demand(ENCODED, 5, 3)), List.of(Map.of(RED, 2L, BLUE, 3L)));
        List<RoutedInputPlan.Resolved<Key>> simulated = new ArrayList<>(plan);
        Map<Key, Long> accepted = new LinkedHashMap<>();
        Map<Key, Long> persistedRemote = new LinkedHashMap<>();
        // Native flow: simulate all, then modulate; any short insert becomes PendingSend(actual key, remainder).
        for (var input : plan) {
            long inserted = input.key().equals(RED) ? 1 : 0; // partial acceptance / unavailable network
            accepted.merge(input.key(), inserted, Long::sum);
            persistedRemote.merge(input.key(), input.amount() - inserted, Long::sum);
        }
        Map<Key, Long> reloadedRemote = new LinkedHashMap<>(persistedRemote);
        assertEquals(plan, simulated);
        assertEquals(Map.of(RED, 1L, BLUE, 3L), reloadedRemote);
        for (var input : plan) {
            assertEquals(input.amount(), accepted.get(input.key()) + reloadedRemote.get(input.key()));
            assertNotEquals(ENCODED, input.key());
        }
    }

    @Test
    void pinsTargetsIncludingAbsenceAndIsolatesSuccessFailureAndNestedAttempts() {
        var outer = new RoutingAttempt<String, Object>();
        Object adapter = new Object();
        AtomicInteger lookups = new AtomicInteger();
        assertSame(adapter, outer.target("remote", () -> { lookups.incrementAndGet(); return adapter; }));
        assertSame(adapter, outer.target("remote", () -> { fail("Late lookup could fail after earlier sends"); return null; }));
        assertEquals(1, lookups.get());
        assertNull(outer.target("missing", () -> null));
        assertNull(outer.target("missing", () -> adapter));
        var inner = new RoutingAttempt<String, Object>();
        assertFalse(inner.isRouted());
        inner.setSourceIndices(List.of(7));
        outer.setSourceIndices(List.of(1, 1));
        assertEquals(7, inner.sourceIndex(0));
        assertEquals(1, outer.sourceIndex(0));
        assertNull(inner.target("remote", () -> null));
        assertSame(adapter, outer.target("remote", () -> null));
        assertThrows(IllegalStateException.class, () -> new RoutingAttempt<>().sourceIndex(0));
    }

    @Test
    void lateNetworkFailureCommitsToBufferInsteadOfReturningFalseAfterPartialSend() {
        var attempt = new RoutingAttempt<String, Object>();
        Object oldAdapter = new Object();
        Object replacement = new Object();
        Object bufferOnly = new Object();
        attempt.target("remote", () -> oldAdapter);
        assertSame(bufferOnly, attempt.commitTarget("remote", () -> null, bufferOnly),
                "A disappeared destination must queue its entire allocation, not fail and reinject earlier sends");
        assertSame(replacement, attempt.commitTarget("remote", () -> replacement, bufferOnly),
                "Use current capability, never the cached detached simulation adapter");
        assertThrows(IllegalStateException.class,
                () -> attempt.commitTarget("not simulated", () -> replacement, bufferOnly));
        attempt.target("missing", () -> null);
        assertThrows(IllegalStateException.class,
                () -> attempt.commitTarget("missing", () -> replacement, bufferOnly));
    }
}
