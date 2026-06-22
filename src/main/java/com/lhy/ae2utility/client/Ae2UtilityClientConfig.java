package com.lhy.ae2utility.client;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 客户端 JEI 相关选项（生成 {@code config/ae2utility-client.toml}）。
 */
public final class Ae2UtilityClientConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    /**
     * 为 {@code true}（默认）时：仅背包中具备编码能力的无线终端即可在 JEI 使用样板编码与高亮，
     * 并通过 {@link com.lhy.ae2utility.jei.CraftableStateCache} 向服务端查询「是否已有样板」。
     * <br>为 {@code false} 时：必须已打开样板编码/终端类界面后才启用上述功能，可显著减轻大量配方替补时的卡顿。
     */
    public static final ForgeConfigSpec.BooleanValue ALLOW_JEI_PATTERN_ENCODE_WITHOUT_OPEN_TERMINAL;

    static {
        ALLOW_JEI_PATTERN_ENCODE_WITHOUT_OPEN_TERMINAL = BUILDER
                .comment(
                        "If true (default), JEI encode works when you carry a wireless encoding terminal without opening it.",
                        "If false, require an open pattern-encoding menu (AE-style).")
                .translation("ae2utility.config.allowJeiPatternEncodeWithoutOpenTerminal")
                .define("allowJeiPatternEncodeWithoutOpenTerminal", true);
        SPEC = BUILDER.build();
    }

    private Ae2UtilityClientConfig() {
    }

    public static boolean allowJeiPatternEncodeWithoutOpenTerminal() {
        return ALLOW_JEI_PATTERN_ENCODE_WITHOUT_OPEN_TERMINAL.get();
    }
}
