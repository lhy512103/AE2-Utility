package com.lhy.ae2utility.card;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import appeng.api.crafting.IPatternDetails;

class NbtTearScopeTest {
    @AfterEach
    void cleanup() {
        NbtTearCardThreadLocal.clear();
        NbtTearCraftingContext.clear();
        NbtTearPatternContext.clear();
        NbtTearSimulationEnv.clear();
    }

    @Test
    void nestedSimulationMasksRealExecutionAndRestoresAfterRollbackException() {
        var real = new NbtTearFilter(List.of());
        var simulated = new NbtTearFilter(List.of());
        try (var execution = NbtTearCardThreadLocal.enter(real)) {
            assertThrows(IllegalStateException.class, () -> {
                try (var masked = NbtTearCardThreadLocal.enter(null);
                        var simulation = NbtTearCraftingContext.enter(simulated);
                        var service = NbtTearSimulationEnv.enter(null)) {
                    assertNull(NbtTearCardThreadLocal.get());
                    assertSame(simulated, NbtTearCraftingContext.get());
                    try (var nested = NbtTearCraftingContext.enter(null)) {
                        assertNull(NbtTearCraftingContext.get());
                    }
                    assertSame(simulated, NbtTearCraftingContext.get());
                    throw new IllegalStateException("rollback");
                }
            });
            assertSame(real, NbtTearCardThreadLocal.get());
            assertNull(NbtTearCraftingContext.get());
            assertNull(NbtTearSimulationEnv.getCraftingService());
        }
        assertNull(NbtTearCardThreadLocal.get());
    }

    @Test
    void sameValueNestingNullMaskingAndIdempotentCloseRestoreExactly() {
        var filter = NbtTearFilter.DEFAULT;
        try (var outer = NbtTearCardThreadLocal.enter(filter)) {
            var inner = NbtTearCardThreadLocal.enter(filter);
            inner.close();
            inner.close();
            assertSame(filter, NbtTearCardThreadLocal.get());
            try (var disabled = NbtTearCardThreadLocal.enter(null)) {
                assertNull(NbtTearCardThreadLocal.get());
            }
            assertSame(filter, NbtTearCardThreadLocal.get());
        }
        assertNull(NbtTearCardThreadLocal.get());
    }

    @Test
    void contextsAreThreadConfinedAndWrongThreadCannotCloseAnOuterScope() throws InterruptedException {
        var scope = NbtTearCardThreadLocal.enter(NbtTearFilter.DEFAULT);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                assertNull(NbtTearCardThreadLocal.get());
                assertThrows(IllegalStateException.class, scope::close);
                try (var independent = NbtTearCardThreadLocal.enter(new NbtTearFilter(List.of()))) {
                    assertNotNull(NbtTearCardThreadLocal.get());
                }
                assertNull(NbtTearCardThreadLocal.get());
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        thread.start();
        thread.join();
        assertNull(failure.get());
        assertSame(NbtTearFilter.DEFAULT, NbtTearCardThreadLocal.get());
        scope.close();
        assertNull(NbtTearCardThreadLocal.get());
    }

    @Test
    void scopedIterableRestoresCallerOnConstructionIterationAndPredicateFailure() {
        IPatternDetails outer = pattern();
        IPatternDetails inner = pattern();
        Iterable<Integer> delegate = () -> {
            assertSame(inner, NbtTearPatternContext.get());
            return new Iterator<>() {
                public boolean hasNext() {
                    assertSame(inner, NbtTearPatternContext.get());
                    return true;
                }
                public Integer next() {
                    assertSame(inner, NbtTearPatternContext.get());
                    throw new IllegalArgumentException("lazy predicate failure");
                }
            };
        };
        var wrapped = NbtTearScopedIterable.wrap(delegate, () -> NbtTearPatternContext.enter(inner));
        try (var caller = NbtTearPatternContext.enter(outer)) {
            var iterator = wrapped.iterator();
            assertSame(outer, NbtTearPatternContext.get());
            assertTrue(iterator.hasNext());
            assertSame(outer, NbtTearPatternContext.get());
            assertThrows(IllegalArgumentException.class, iterator::next);
            assertSame(outer, NbtTearPatternContext.get());
            assertThrows(UnsupportedOperationException.class, iterator::remove);
            assertSame(outer, NbtTearPatternContext.get());
        }
        assertNull(NbtTearPatternContext.get());
    }

    @Test
    void lazyIteratorFactoryAndHasNextExceptionsRestorePattern() {
        IPatternDetails outer = pattern();
        IPatternDetails inner = pattern();
        Iterable<Integer> factoryFailure = () -> {
            assertSame(inner, NbtTearPatternContext.get());
            throw new IllegalStateException("iterator creation");
        };
        Iterable<Integer> predicateFailure = () -> new Iterator<>() {
            public boolean hasNext() {
                assertSame(inner, NbtTearPatternContext.get());
                throw new IllegalStateException("hasNext predicate");
            }
            public Integer next() { return 1; }
        };
        try (var caller = NbtTearPatternContext.enter(outer)) {
            assertThrows(IllegalStateException.class, () -> NbtTearScopedIterable.wrap(factoryFailure,
                    () -> NbtTearPatternContext.enter(inner)).iterator());
            assertSame(outer, NbtTearPatternContext.get());
            var iterator = NbtTearScopedIterable.wrap(predicateFailure,
                    () -> NbtTearPatternContext.enter(inner)).iterator();
            assertThrows(IllegalStateException.class, iterator::hasNext);
            assertSame(outer, NbtTearPatternContext.get());
        }
    }

    @Test
    void scopeRestorationUsesTheSameGenericMechanismForNonNullServices() {
        // Tests the exact generic primitive used by NbtTearSimulationEnv without
        // constructing a live grid/service in a plain JVM unit test.
        ThreadLocal<Object> service = new ThreadLocal<>();
        Object simulation = new Object();
        Object execution = new Object();
        try (var outer = NbtTearScope.enter(service, simulation)) {
            assertThrows(RuntimeException.class, () -> {
                try (var nested = NbtTearScope.enter(service, execution)) {
                    assertSame(execution, service.get());
                    throw new RuntimeException("CPU extract");
                }
            });
            assertSame(simulation, service.get());
        }
        assertNull(service.get());
    }

    private static IPatternDetails pattern() {
        return (IPatternDetails) Proxy.newProxyInstance(IPatternDetails.class.getClassLoader(),
                new Class<?>[] { IPatternDetails.class }, (proxy, method, args) -> null);
    }
}
