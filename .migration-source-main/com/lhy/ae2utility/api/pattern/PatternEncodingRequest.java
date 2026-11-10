package com.lhy.ae2utility.api.pattern;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.GenericStack;
import net.minecraft.resources.ResourceLocation;

/** Immutable description of one pattern encoding operation. */
public record PatternEncodingRequest(
        List<List<GenericStack>> inputs,
        List<GenericStack> outputs,
        @Nullable ResourceLocation recipeId,
        String patternName,
        String providerSearchKey,
        String providerDisplayName,
        PatternUploadMode uploadMode,
        boolean substitute,
        boolean substituteFluids,
        boolean preserveInputOrder,
        boolean craftingRecipeHint) {

    public PatternEncodingRequest {
        if (inputs == null || outputs == null || uploadMode == null) {
            throw new IllegalArgumentException("inputs, outputs and uploadMode are required");
        }
        List<List<GenericStack>> copiedInputs = new ArrayList<>(inputs.size());
        for (List<GenericStack> slot : inputs) {
            copiedInputs.add(slot == null ? List.of() : List.copyOf(slot));
        }
        inputs = List.copyOf(copiedInputs);
        outputs = List.copyOf(outputs);
        patternName = patternName == null ? "" : patternName;
        providerSearchKey = providerSearchKey == null ? "" : providerSearchKey;
        providerDisplayName = providerDisplayName == null ? "" : providerDisplayName;
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("at least one output is required");
        }
    }
}