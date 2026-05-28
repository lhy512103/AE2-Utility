package com.lhy.ae2utility.client;

/**
 * Client-side persistent state for recipe finder UI filters.
 */
public final class RecipeFinderScreenState {
    private static final FilterState STATE = new FilterState();

    private RecipeFinderScreenState() {
    }

    public static FilterState get() {
        return STATE;
    }

    public static class FilterState {
        public String modValue = "all";
        public String machineValue = "all";
        public String materialValue = "all";
        public String outputValue = "all";
        public String excludeTagValue = "all";
        public boolean encodableOnly;
        public String keyword = "";
    }
}
