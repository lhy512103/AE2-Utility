package com.lhy.ae2utility.util;

public enum PatternEncodingPreviewMode {
    PROCESSING,
    CRAFTING,
    SMITHING_TABLE,
    STONECUTTING;

    public static PatternEncodingPreviewMode byOrdinal(int ordinal) {
        PatternEncodingPreviewMode[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return PROCESSING;
        }
        return values[ordinal];
    }
}
