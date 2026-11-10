package com.lhy.ae2utility.client.jei;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EncodingPreparationTest {
    @Test
    void repeatedTickDrawTooltipAndIngredientRequestsShareOnePreparation() {
        EncodingPreparation preparation = new EncodingPreparation();
        int queries = 0;
        for (int callback = 0; callback < 100; callback++) {
            preparation.contextChanged("same-layout");
            if (preparation.begin(42)) {
                queries++;
            }
        }
        assertEquals(1, queries);
        assertTrue(preparation.begin(43));
        assertFalse(preparation.begin(43));
    }

    @Test
    void invalidationDoesNotGrantAnotherQueryInTheSameTick() {
        EncodingPreparation preparation = new EncodingPreparation();
        assertTrue(preparation.contextChanged("bookmarks-1/menu-1/network-1"));
        assertTrue(preparation.begin(10));
        assertTrue(preparation.contextChanged("bookmarks-2/menu-1/network-1"));
        assertFalse(preparation.begin(10));
        assertTrue(preparation.contextChanged("bookmarks-2/menu-2/network-1"));
        assertFalse(preparation.begin(10));
        assertTrue(preparation.contextChanged("bookmarks-2/menu-2/network-2"));
        assertFalse(preparation.begin(10));
        assertTrue(preparation.begin(11));
        assertFalse(preparation.contextChanged("bookmarks-2/menu-2/network-2"));
    }

    @Test
    void preparationBudgetIsPerLayoutNotGlobal() {
        assertTrue(new EncodingPreparation().begin(9));
        assertTrue(new EncodingPreparation().begin(9));
    }

    @Test
    void previewsAreStrictlyHoverAndConfigurationGated() {
        for (boolean ingredients : new boolean[] { false, true }) {
            for (boolean terminal : new boolean[] { false, true }) {
                assertFalse(EncodingPreparation.shouldPreview(true, false, ingredients, terminal));
                assertFalse(EncodingPreparation.shouldPreview(false, true, ingredients, terminal));
                assertEquals(ingredients || terminal,
                        EncodingPreparation.shouldPreview(true, true, ingredients, terminal));
            }
        }
    }
}
