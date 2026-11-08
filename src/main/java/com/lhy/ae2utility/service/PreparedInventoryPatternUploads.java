package com.lhy.ae2utility.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;

/** Server-thread-only authorization; identities contain one complete serialized pattern. */
final class PreparedInventoryPatternUploads {
    private record Selection(int slot, CompoundTag stack) {}

    private final Map<UUID, Selection> selections = new HashMap<>();

    static boolean canReplacePending(boolean hasPendingPattern, boolean borrowedCopy) {
        return !hasPendingPattern || borrowedCopy;
    }

    void prepare(UUID playerId, int slot, CompoundTag stack) {
        selections.put(playerId, new Selection(slot, stack.copy()));
    }

    int slot(UUID playerId) {
        Selection selection = selections.get(playerId);
        return selection == null ? -1 : selection.slot();
    }

    boolean matches(UUID playerId, int slot, CompoundTag stack) {
        Selection selection = selections.get(playerId);
        return selection != null && selection.slot() == slot && selection.stack().equals(stack);
    }

    boolean clear(UUID playerId) {
        return selections.remove(playerId) != null;
    }
}
