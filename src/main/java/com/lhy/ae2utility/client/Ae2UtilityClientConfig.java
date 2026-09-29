package com.lhy.ae2utility.client;

import java.util.List;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 客户端 JEI 相关选项（生成 {@code config/ae2utility-client.toml}）。
 */
public final class Ae2UtilityClientConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ALLOW_JEI_PATTERN_ENCODE_WITHOUT_OPEN_TERMINAL;
    public static final ForgeConfigSpec.BooleanValue PREFER_FAVORITES_FOR_PATTERN_ENCODING;
    public static final ForgeConfigSpec.BooleanValue SHOW_JEI_BATCH_ENCODE_FULL_CATEGORY_BUTTON;

    private static final List<Option> OPTIONS;

    static {
        ALLOW_JEI_PATTERN_ENCODE_WITHOUT_OPEN_TERMINAL = BUILDER
                .comment(
                        "If true (default), JEI encode works when you carry a wireless encoding terminal without opening it.",
                        "If false, require an open pattern-encoding menu (AE-style).")
                .translation("ae2utility.config.allowJeiPatternEncodeWithoutOpenTerminal")
                .define("allowJeiPatternEncodeWithoutOpenTerminal", true);
        PREFER_FAVORITES_FOR_PATTERN_ENCODING = BUILDER
                .comment(
                        "Prefer JEI bookmarks when choosing among pattern input alternatives.",
                        "If false, do not read bookmarks and preserve the original alternative order.")
                .translation("ae2utility.config.preferFavoritesForPatternEncoding")
                .define("preferFavoritesForPatternEncoding", true);
        SHOW_JEI_BATCH_ENCODE_FULL_CATEGORY_BUTTON = BUILDER
                .comment(
                        "Show the batch-encode-all-pages-in-this-JEI-category button.",
                        "Turn off if players accidentally click it and cause lag on multiplayer. Takes effect after reopening JEI.")
                .translation("ae2utility.config.showJeiBatchEncodeFullCategoryButton")
                .define("showJeiBatchEncodeFullCategoryButton", true);
        SPEC = BUILDER.build();

        OPTIONS = List.of(
                new Option("ae2utility.config.allowJeiPatternEncodeWithoutOpenTerminal",
                        ALLOW_JEI_PATTERN_ENCODE_WITHOUT_OPEN_TERMINAL),
                new Option("ae2utility.config.preferFavoritesForPatternEncoding",
                        PREFER_FAVORITES_FOR_PATTERN_ENCODING),
                new Option("ae2utility.config.showJeiBatchEncodeFullCategoryButton",
                        SHOW_JEI_BATCH_ENCODE_FULL_CATEGORY_BUTTON));
    }

    private Ae2UtilityClientConfig() {
    }

    public static List<Option> options() {
        return OPTIONS;
    }

    public static boolean allowJeiPatternEncodeWithoutOpenTerminal() {
        return ALLOW_JEI_PATTERN_ENCODE_WITHOUT_OPEN_TERMINAL.get();
    }

    public static boolean preferFavoritesForPatternEncoding() {
        return PREFER_FAVORITES_FOR_PATTERN_ENCODING.get();
    }

    public static boolean showJeiBatchEncodeFullCategoryButton() {
        return SHOW_JEI_BATCH_ENCODE_FULL_CATEGORY_BUTTON.get();
    }

    public record Option(String key, ForgeConfigSpec.BooleanValue value) {
    }
}
