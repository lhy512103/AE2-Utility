package com.lhy.ae2utility.emi;

import java.util.ArrayList;
import java.util.List;

import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiFavorite;
import dev.emi.emi.runtime.EmiFavorites;

/** Reads a stable snapshot of EMI's user-managed favorites on the client thread. */
final class EmiFavoriteStacks {
    private EmiFavoriteStacks() {
    }

    static List<EmiStack> snapshot() {
        List<EmiStack> stacks = new ArrayList<>();
        for (EmiFavorite favorite : List.copyOf(EmiFavorites.favorites)) {
            if (favorite == null || favorite.getStack() == null) {
                continue;
            }
            for (EmiStack stack : favorite.getStack().getEmiStacks()) {
                if (stack != null && !stack.isEmpty()) {
                    stacks.add(stack);
                }
            }
        }
        return List.copyOf(stacks);
    }
}