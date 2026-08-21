package com.lhy.ae2utility.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class PatternEncodingPreviewTest {
    @Test
    void resolveModeUsesCraftingHintOnlyForSmallRecipes() {
        assertEquals(PatternEncodingPreviewMode.CRAFTING,
                PatternEncodingPreview.resolveMode(null, true, 9, null));
        assertEquals(PatternEncodingPreviewMode.PROCESSING,
                PatternEncodingPreview.resolveMode(null, true, 10, null));
        assertEquals(PatternEncodingPreviewMode.PROCESSING,
                PatternEncodingPreview.resolveMode(null, false, 3, null));
    }

    @Test
    void processingPreviewPadsVisiblePageAndDropsSubstitution() {
        PatternEncodingPreview preview = PatternEncodingPreview.fromSlots(
                PatternEncodingPreviewMode.PROCESSING, List.of(), List.of(), true, true);

        assertEquals(PatternEncodingPreviewMode.PROCESSING, preview.mode());
        assertEquals(9, preview.inputs().size());
        assertEquals(3, preview.outputs().size());
        assertEquals(0, preview.extraInputs());
        assertEquals(0, preview.extraOutputs());
        assertFalse(preview.substituteItems());
        assertFalse(preview.substituteFluids());
    }

    @Test
    void extraCountIgnoresVisiblePage() {
        assertEquals(3, PatternEncodingPreview.extraCount(12, 9));
        assertEquals(2, PatternEncodingPreview.extraCount(5, 3));
        assertEquals(0, PatternEncodingPreview.extraCount(3, 9));
    }

    @Test
    void craftingPreviewPadsToNineAndKeepsSubstitutionFlags() {
        PatternEncodingPreview preview = PatternEncodingPreview.fromSlots(
                PatternEncodingPreviewMode.CRAFTING, List.of(), List.of(), true, true);

        assertEquals(9, preview.inputs().size());
        assertEquals(1, preview.outputs().size());
        assertEquals(0, preview.extraInputs());
        assertTrue(preview.substituteItems());
        assertTrue(preview.substituteFluids());
    }

    @Test
    void presentCountIgnoresEmptySlots() {
        assertEquals(0, PatternEncodingPreview.presentCount(List.of()));
        assertEquals(0, PatternEncodingPreview.presentCount(Collections.nCopies(3, null)));
    }

    @Test
    void unknownModeOrdinalFallsBackToProcessing() {
        assertEquals(PatternEncodingPreviewMode.PROCESSING, PatternEncodingPreviewMode.byOrdinal(-1));
        assertEquals(PatternEncodingPreviewMode.CRAFTING, PatternEncodingPreviewMode.byOrdinal(1));
    }
}
