package com.lhy.ae2utility.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 客户端 JEI 相关选项（生成 {@code config/ae2utility-client.toml}）。
 */
public final class Ae2UtilityClientConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    /**
     * 为 {@code true}（默认）时：仅背包/Curios 中有无线编码终端即可在 JEI 使用样板编码与高亮，并通过 {@link com.lhy.ae2utility.jei.CraftableStateCache}
     * 向服务端查询「是否已有样板」。<br>
     * 为 {@code false} 时：与 AE 拉取配方类似，必须<strong>已打开</strong>样板编码/终端类界面后才启用上述功能，可显著减轻大量配方替补时的卡顿与 MSPT。
     */
    public static final ModConfigSpec.BooleanValue ALLOW_JEI_PATTERN_ENCODE_WITHOUT_OPEN_TERMINAL;

    /**
     * 为 {@code true}（默认）时：JEI 左上角显示「一键编码当前机器分类全部配方」按钮；{@code false} 时隐藏以防联机误点卡顿。
     */
    public static final ModConfigSpec.BooleanValue SHOW_JEI_BATCH_ENCODE_FULL_CATEGORY_BUTTON;

    /** 是否在选项「控制」里注册「清空全部 JEI 书签」快捷键（默认 Ctrl+A，可自行改键）。 */
    public static final ModConfigSpec.BooleanValue ENABLE_CLEAR_ALL_JEI_BOOKMARKS_HOTKEY;

    static {
        ALLOW_JEI_PATTERN_ENCODE_WITHOUT_OPEN_TERMINAL = BUILDER
                .comment(
                        "If true (default), JEI encode works when you carry a wireless encoding terminal without opening it.",
                        "If false, require an open pattern-encoding menu (AE-style).")
                .translation("ae2utility.config.allowJeiPatternEncodeWithoutOpenTerminal")
                .define("allowJeiPatternEncodeWithoutOpenTerminal", true);
        SHOW_JEI_BATCH_ENCODE_FULL_CATEGORY_BUTTON = BUILDER
                .comment(
                        "Show the batch-encode-all-pages-in-this-JEI-category button next to substitutions.",
                        "Turn off if players accidentally click it and cause lag on multiplayer.")
                .translation("ae2utility.config.showJeiBatchEncodeFullCategoryButton")
                .define("showJeiBatchEncodeFullCategoryButton", true);
        ENABLE_CLEAR_ALL_JEI_BOOKMARKS_HOTKEY = BUILDER
                .comment(
                        "When true (default): register 'Clear all JEI bookmarks' in Controls.",
                        "Default binding is CTRL+A; change it together with modifiers in Vanilla controls menu.",
                        "When false: the hotkey is not registered.")
                .translation("ae2utility.config.enableClearAllJeiBookmarksHotkey")
                .define("enableClearAllJeiBookmarksHotkey", true);
        SPEC = BUILDER.build();
    }

    private Ae2UtilityClientConfig() {
    }

    public static boolean allowJeiPatternEncodeWithoutOpenTerminal() {
        return ALLOW_JEI_PATTERN_ENCODE_WITHOUT_OPEN_TERMINAL.get();
    }

    public static boolean showJeiBatchEncodeFullCategoryButton() {
        return SHOW_JEI_BATCH_ENCODE_FULL_CATEGORY_BUTTON.get();
    }

    public static boolean enableClearAllJeiBookmarksHotkey() {
        return ENABLE_CLEAR_ALL_JEI_BOOKMARKS_HOTKEY.get();
    }
}
