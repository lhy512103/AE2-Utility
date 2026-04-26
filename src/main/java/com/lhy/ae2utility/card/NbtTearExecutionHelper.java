package com.lhy.ae2utility.card;

import java.util.List;

import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.lhy.ae2utility.debug.NbtTearCardDebug;

public final class NbtTearExecutionHelper {

    private NbtTearExecutionHelper() {
    }

    public static boolean pushSparseInputsWithTear(
            KeyCounter[] inputHolder,
            IPatternDetails.PatternInputSink inputSink,
            List<GenericStack> sparseInputs) {
        var allInputs = new KeyCounter();
        for (var counter : inputHolder) {
            allInputs.addAll(counter);
        }

        for (var sparseInput : sparseInputs) {
            if (sparseInput == null) {
                continue;
            }

            var expectedKey = sparseInput.what();
            var amount = sparseInput.amount();
            var actualKey = resolveAvailableKey(allInputs, expectedKey, amount);
            long available = actualKey == null ? 0 : allInputs.get(actualKey);

            if (actualKey == null || available < amount) {
                throw new RuntimeException("Expected at least %d of %s when pushing pattern, but only %d available"
                        .formatted(amount, expectedKey, available));
            }

            NbtTearCardDebug.logFuzzyCraftSearch("push_inputs", expectedKey, actualKey, true, "push_actual_key");
            inputSink.pushInput(actualKey, amount);
            allInputs.remove(actualKey, amount);
        }

        return true;
    }

    private static AEKey resolveAvailableKey(KeyCounter allInputs, AEKey expectedKey, long amount) {
        if (allInputs.get(expectedKey) >= amount) {
            return expectedKey;
        }

        NbtTearFilter filter = NbtTearCardThreadLocal.get();
        if (filter == null) {
            filter = NbtTearCraftingContext.get();
        }
        if (filter == null) {
            NbtTearCardDebug.logFuzzyCraftSearch("push_inputs", expectedKey, null, false, "filter_null");
            return null;
        }

        for (var fuzzy : allInputs.findFuzzy(expectedKey, FuzzyMode.IGNORE_ALL)) {
            AEKey candidate = fuzzy.getKey();
            if (allInputs.get(candidate) >= amount
                    && NbtTearFilter.matchesUnlockExpected(expectedKey, candidate, filter)) {
                NbtTearCardDebug.logFuzzyCraftSearch("push_inputs", expectedKey, candidate, true, "tear_resolved");
                return candidate;
            }
        }

        NbtTearCardDebug.logFuzzyCraftSearch("push_inputs", expectedKey, null, false, "no_matching_candidate");
        return null;
    }
}
