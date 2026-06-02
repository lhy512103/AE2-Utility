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

    /**
     * 为 {@code true}（默认）时：与 ExtendedAE Plus 供应器上传配合，在「同一批量上传会话」内，
     * 若某个搜索关键字曾选过供应器，则后续同关键字匹配到<strong>多个</strong>供应器时自动复用上次选择的同名供应器，
     * 减少重复弹窗。按供应器名称在当前列表里重新匹配，因此不会因列表顺序变化而上传到错误位置。<br>
     * 仍然受 ExtendedAE Plus 的「唯一匹配自动上传」开关约束：该开关关闭时不会自动复用。<br>
     * 为 {@code false} 时：每条样板都需手动选择供应器（除非过滤后唯一）。
     */
    public static final ModConfigSpec.BooleanValue REUSE_PROVIDER_WITHIN_BATCH;

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
        REUSE_PROVIDER_WITHIN_BATCH = BUILDER
                .comment(
                        "When true (default): within one batch upload session, reuse the previously chosen provider",
                        "(matched by provider name) when the same search key resolves to multiple providers,",
                        "to reduce repeated provider-select popups. Still requires ExtendedAE Plus's",
                        "'auto upload unique match' toggle to be ON.",
                        "When false: every pattern must be chosen manually unless the filter is unique.")
                .translation("ae2utility.config.reuseProviderWithinBatch")
                .define("reuseProviderWithinBatch", true);
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

    public static boolean reuseProviderWithinBatch() {
        return REUSE_PROVIDER_WITHIN_BATCH.get();
    }
}
