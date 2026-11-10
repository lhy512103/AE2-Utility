package com.lhy.ae2utility.integration.ae2cs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;

/** Pure preflight allocation. Neither caller budgets nor encoded pattern inputs are mutated. */
public final class RoutedInputPlan {
    private RoutedInputPlan() {
    }

    public record Demand<K>(K key, long amount, int sourceIndex) {
        public Demand {
            if (key == null || amount <= 0 || sourceIndex < 0) {
                throw new IllegalArgumentException("Invalid routed input demand");
            }
        }
    }

    /** sourceIndex retains routing metadata when one encoded input uses several actual variants. */
    public record Resolved<K>(K key, long amount, int sourceIndex) {
    }

    public static <K> Optional<List<Resolved<K>>> resolve(List<Demand<K>> demands,
            List<? extends Map<K, Long>> holders, BiPredicate<K, K> allowsAlternative) {
        Map<K, Long> remaining = new LinkedHashMap<>();
        for (Map<K, Long> holder : holders) {
            for (var entry : holder.entrySet()) {
                if (entry.getKey() == null || entry.getValue() < 0) {
                    return Optional.empty();
                }
                if (entry.getValue() > 0) {
                    try {
                        remaining.merge(entry.getKey(), entry.getValue(), Math::addExact);
                    } catch (ArithmeticException overflow) {
                        return Optional.empty();
                    }
                }
            }
        }

        List<List<Resolved<K>>> allocations = new ArrayList<>();
        long[] missing = new long[demands.size()];
        // Reserve exact matches for every demand before allowing any fuzzy demand to borrow them.
        for (int i = 0; i < demands.size(); i++) {
            Demand<K> demand = demands.get(i);
            allocations.add(new ArrayList<>());
            missing[i] = demand.amount();
            allocate(demand, demand.key(), remaining, allocations.get(i), missing, i);
        }
        for (int i = 0; i < demands.size(); i++) {
            Demand<K> demand = demands.get(i);
            for (K candidate : remaining.keySet()) {
                if (missing[i] == 0) {
                    break;
                }
                if (!candidate.equals(demand.key()) && allowsAlternative.test(demand.key(), candidate)) {
                    allocate(demand, candidate, remaining, allocations.get(i), missing, i);
                }
            }
            if (missing[i] != 0) {
                return Optional.empty();
            }
        }
        // The CPU transfers ownership of the ENTIRE extracted holder on true. Never discard extras.
        if (remaining.values().stream().anyMatch(amount -> amount != 0)) {
            return Optional.empty();
        }
        List<Resolved<K>> result = new ArrayList<>();
        allocations.forEach(result::addAll);
        return Optional.of(Collections.unmodifiableList(result));
    }

    private static <K> void allocate(Demand<K> demand, K candidate, Map<K, Long> remaining,
            List<Resolved<K>> allocation, long[] missing, int index) {
        long take = Math.min(missing[index], remaining.getOrDefault(candidate, 0L));
        if (take > 0) {
            remaining.put(candidate, remaining.get(candidate) - take);
            missing[index] -= take;
            allocation.add(new Resolved<>(candidate, take, demand.sourceIndex()));
        }
    }
}
