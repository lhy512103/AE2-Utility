package com.lhy.ae2utility.integration.ae2cs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import appeng.api.config.Actionable;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.lhy.ae2utility.card.NbtTearFilter;

/** AE2CS 1.2's native loop consumes this projection for BOTH local and remote destinations. */
public final class Ae2CsRoutingAdapter {
    /** Native modulation queues the complete actual-key amount when a preflighted target vanishes. */
    public static final PatternProviderTarget BUFFER_ONLY_TARGET = new PatternProviderTarget() {
        @Override
        public long insert(AEKey what, long amount, Actionable mode) {
            return 0;
        }

        @Override
        public boolean containsPatternInput(Set<AEKey> patternInputs) {
            return false;
        }
    };

    private Ae2CsRoutingAdapter() {
    }

    /** Only this pre-side-effect rejection is converted to pushPattern=false by the mixin. */
    public static final class UnresolvedInputs extends RuntimeException {
        public UnresolvedInputs() {
            super("AE2CS routed inputs do not match the extracted input budget", null, false, false);
        }
    }

    public static List<GenericStack> resolve(List<GenericStack> sparseInputs, KeyCounter[] inputHolder,
            NbtTearFilter filter, RoutingAttempt<?, ?> attempt) {
        List<RoutedInputPlan.Demand<AEKey>> demands = new ArrayList<>();
        for (int index = 0; index < sparseInputs.size(); index++) {
            GenericStack stack = sparseInputs.get(index);
            if (stack != null) {
                if (stack.amount() <= 0) {
                    throw new UnresolvedInputs();
                }
                demands.add(new RoutedInputPlan.Demand<>(stack.what(), stack.amount(), index));
            }
        }
        List<Map<AEKey, Long>> budgets = new ArrayList<>();
        for (KeyCounter holder : inputHolder) {
            Map<AEKey, Long> budget = new LinkedHashMap<>();
            for (var entry : holder) {
                budget.put(entry.getKey(), entry.getLongValue());
            }
            budgets.add(budget);
        }
        var resolved = RoutedInputPlan.resolve(demands, budgets,
                (expected, candidate) -> filter != null
                        && NbtTearFilter.matchesUnlockExpected(expected, candidate, filter))
                .orElseThrow(UnresolvedInputs::new);
        attempt.setSourceIndices(resolved.stream().map(RoutedInputPlan.Resolved::sourceIndex).toList());
        return resolved.stream().map(input -> new GenericStack(input.key(), input.amount())).toList();
    }
}
