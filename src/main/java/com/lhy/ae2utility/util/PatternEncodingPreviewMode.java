package com.lhy.ae2utility.util;

public enum PatternEncodingPreviewMode {
    PROCESSING,
    CRAFTING,
    SMITHING_TABLE,
    STONECUTTING;

    public static PatternEncodingPreviewMode byOrdinal(int ordinal) {
        PatternEncodingPreviewMode[] values = values();
        return ordinal < 0 || ordinal >= values.length ? PROCESSING : values[ordinal];
    }
}
