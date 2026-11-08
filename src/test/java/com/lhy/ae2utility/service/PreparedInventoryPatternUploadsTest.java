package com.lhy.ae2utility.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class PreparedInventoryPatternUploadsTest {
    private final UUID player = UUID.randomUUID();
    private final PreparedInventoryPatternUploads uploads = new PreparedInventoryPatternUploads();

    @Test
    void refusesToReplaceAGenuinePendingItemButAllowsBorrowedCopies() {
        assertFalse(PreparedInventoryPatternUploads.canReplacePending(true, false));
        assertTrue(PreparedInventoryPatternUploads.canReplacePending(true, true));
        assertTrue(PreparedInventoryPatternUploads.canReplacePending(false, false));
        assertTrue(PreparedInventoryPatternUploads.canReplacePending(false, true));
    }

    @Test
    void rejectsUploadsWithoutPreparation() {
        assertEquals(-1, uploads.slot(player));
        assertFalse(uploads.matches(player, 3, pattern("expensive")));
    }

    @Test
    void bindsThePreparedSlotEvenWhenAnotherSlotContainsAnIdenticalPattern() {
        uploads.prepare(player, 3, pattern("expensive"));
        assertTrue(uploads.matches(player, 3, pattern("expensive")));
        assertFalse(uploads.matches(player, 4, pattern("expensive")));
    }

    @Test
    void rejectsReplacementPatternsWithDifferentRecipeNbt() {
        uploads.prepare(player, 3, pattern("expensive"));
        assertFalse(uploads.matches(player, 3, pattern("cheap")));
    }

    @Test
    void snapshotsNestedNbtAndForgeCapabilities() {
        CompoundTag original = pattern("expensive");
        CompoundTag capability = new CompoundTag();
        capability.putString("owner", "first");
        original.put("ForgeCaps", capability);
        CompoundTag expected = original.copy();
        uploads.prepare(player, 3, original);
        original.getCompound("tag").putString("recipe", "cheap");
        capability.putString("owner", "second");
        assertTrue(uploads.matches(player, 3, expected));
        assertFalse(uploads.matches(player, 3, original));
    }

    @Test
    void replacingPreparationRevokesThePreviousSlotAndPattern() {
        uploads.prepare(player, 3, pattern("first"));
        uploads.prepare(player, 4, pattern("second"));
        assertFalse(uploads.matches(player, 3, pattern("first")));
        assertTrue(uploads.matches(player, 4, pattern("second")));
    }

    @Test
    void completionCancellationAndDisconnectRevokeAuthorization() {
        uploads.prepare(player, 3, pattern("expensive"));
        assertTrue(uploads.clear(player));
        assertFalse(uploads.clear(player));
        assertFalse(uploads.matches(player, 3, pattern("expensive")));
        assertEquals(-1, uploads.slot(player));
    }

    @Test
    void authorizationsAreIsolatedPerPlayer() {
        UUID other = UUID.randomUUID();
        uploads.prepare(player, 3, pattern("first"));
        uploads.prepare(other, 4, pattern("second"));
        uploads.clear(player);
        assertTrue(uploads.matches(other, 4, pattern("second")));
        assertFalse(uploads.matches(other, 3, pattern("first")));
    }

    private static CompoundTag pattern(String recipe) {
        CompoundTag stack = new CompoundTag();
        stack.putString("id", "ae2:processing_pattern");
        stack.putByte("Count", (byte) 1);
        CompoundTag tag = new CompoundTag();
        tag.putString("recipe", recipe);
        stack.put("tag", tag);
        return stack;
    }
}
