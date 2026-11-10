package com.lhy.ae2utility.emi;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.screen.RecipeDisplay;
import dev.emi.emi.screen.RecipeScreen;
import dev.emi.emi.screen.RecipeTab;

/**
 * Minimal reflective access to {@link RecipeScreen}'s displayed recipe set.
 *
 * <p>EMI exposes the focused category ({@link RecipeScreen#getFocusedCategory()}) but not
 * the recipes actually shown on the current page / current tab (which can be a filtered
 * subset when browsing uses/recipes of an item). There is no public API for this, so we
 * read the private {@code tabs} / {@code tab} / {@code page} fields. {@link RecipeTab} and
 * {@link RecipeDisplay} themselves expose everything else publicly.</p>
 */
public final class EmiRecipeScreenAccess {
    private static Field tabsField;
    private static Field tabField;
    private static Field pageField;
    private static boolean resolved;

    private EmiRecipeScreenAccess() {
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            tabsField = RecipeScreen.class.getDeclaredField("tabs");
            tabField = RecipeScreen.class.getDeclaredField("tab");
            pageField = RecipeScreen.class.getDeclaredField("page");
            tabsField.setAccessible(true);
            tabField.setAccessible(true);
            pageField.setAccessible(true);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            tabsField = null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<RecipeTab> tabs(RecipeScreen screen) {
        resolve();
        if (tabsField == null) {
            return List.of();
        }
        try {
            Object value = tabsField.get(screen);
            return value instanceof List<?> list ? (List<RecipeTab>) list : List.of();
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return List.of();
        }
    }

    private static int intField(RecipeScreen screen, Field field) {
        resolve();
        if (field == null) {
            return -1;
        }
        try {
            return field.getInt(screen);
        } catch (ReflectiveOperationException ignored) {
            return -1;
        }
    }

    private static RecipeTab focusedTab(RecipeScreen screen) {
        List<RecipeTab> tabs = tabs(screen);
        int tab = intField(screen, tabField);
        if (tab < 0 || tab >= tabs.size()) {
            return null;
        }
        return tabs.get(tab);
    }

    /** Recipes visible on the current page of the focused category. */
    public static List<EmiRecipe> getCurrentPageRecipes(RecipeScreen screen) {
        RecipeTab tab = focusedTab(screen);
        if (tab == null) {
            return List.of();
        }
        int page = intField(screen, pageField);
        List<EmiRecipe> out = new ArrayList<>();
        for (RecipeDisplay display : tab.getPage(page)) {
            if (display != null && display.recipe != null) {
                out.add(display.recipe);
            }
        }
        return out;
    }

    /** Recipes across every page of the focused category tab. */
    public static List<EmiRecipe> getCurrentTabRecipes(RecipeScreen screen) {
        RecipeTab tab = focusedTab(screen);
        if (tab == null) {
            return List.of();
        }
        List<EmiRecipe> out = new ArrayList<>();
        for (int p = 0; p < tab.getPageCount(); p++) {
            for (RecipeDisplay display : tab.getPage(p)) {
                if (display != null && display.recipe != null) {
                    out.add(display.recipe);
                }
            }
        }
        return out;
    }
}
