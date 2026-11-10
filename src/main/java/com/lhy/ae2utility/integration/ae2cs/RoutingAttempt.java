package com.lhy.ae2utility.integration.ae2cs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Per-call state only: never persisted and never shared with a later/reentrant push. */
public final class RoutingAttempt<T, A> {
    private List<Integer> sourceIndices;
    private final Map<T, A> targets = new HashMap<>();

    public void setSourceIndices(List<Integer> sourceIndices) {
        this.sourceIndices = List.copyOf(sourceIndices);
    }

    public boolean isRouted() {
        return sourceIndices != null;
    }

    public int sourceIndex(int resolvedIndex) {
        if (sourceIndices == null) {
            throw new IllegalStateException("Routed input preflight has not run");
        }
        return sourceIndices.get(resolvedIndex);
    }

    /** Pin the preflight result, including absence, for all simulated inputs sharing a target. */
    public A target(T target, Supplier<A> lookup) {
        if (!targets.containsKey(target)) {
            targets.put(target, lookup.get());
        }
        return targets.get(target);
    }

    /** Re-resolve at commit: never insert into a stale detached capability or fail after earlier sends. */
    public A commitTarget(T target, Supplier<A> lookup, A bufferOnly) {
        if (!targets.containsKey(target) || targets.get(target) == null) {
            throw new IllegalStateException("Remote target was not preflighted");
        }
        A current = lookup.get();
        return current != null ? current : bufferOnly;
    }
}
