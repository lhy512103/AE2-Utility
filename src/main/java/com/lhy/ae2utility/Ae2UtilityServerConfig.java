package com.lhy.ae2utility;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 逻辑服务端配置（生成 {@code config/ae2utility-server.toml}），在专用服与单机整合包的存档主机侧生效。
 */
public final class Ae2UtilityServerConfig {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    /**
     * 为 {@code true} 时：未打开样板编码类 ME 界面的编码请求一律拒绝（含仅凭背包无线终端「隔空」编码），与客户端「未开终端是否允许」独立。
     */
    public static final ModConfigSpec.BooleanValue REQUIRE_OPEN_PATTERN_ENCODING_MENU_FOR_JEI;

    /**
     * 为 {@code true} 时：拒绝由 JEI「按整台机器分类批量（所有分页）」发起的编码请求（数据包中标记为整类）；可防止玩家自行改客户端配置后仍滥用。不影响「仅当前页」或单次编码箭头。
     */
    public static final ModConfigSpec.BooleanValue BLOCK_JEI_FULL_CATEGORY_BATCH_ENCODE;

    /**
     * 单次「共享 bulk 会话」内最多实际编码的样板条数（超出部分不会编码）；{@code -1} 表示关闭该上限（不截断、不按会话计数）。
     * JEI 全类/当前页、配方树、配方查找器等共用。
     */
    public static final ModConfigSpec.IntValue JEI_BULK_ENCODE_MAX_PATTERNS_PER_SESSION;

    static {
        BUILDER.comment("Pattern encoding policy for dedicated servers / singleplayer host.");
        REQUIRE_OPEN_PATTERN_ENCODING_MENU_FOR_JEI = BUILDER
                .comment("If true, reject JEI / mod encode packets unless a pattern-encoding ME screen is open (blocks wireless-from-inventory).",
                        "Does not remove the client-side option; this rule is stricter when enabled.")
                .translation("ae2utility.serverConfig.requireOpenPatternEncodingMenuForJei")
                .define("requireOpenPatternEncodingMenuForJei", false);
        BLOCK_JEI_FULL_CATEGORY_BATCH_ENCODE = BUILDER
                .comment("If true, reject encode packets tagged as «full JEI category (all pages)» batch encoding.",
                        "Does not affect current-page batch or single-recipe encode. Pair with hiding the button on clients for convenience.")
                .translation("ae2utility.serverConfig.blockJeiFullCategoryBatchEncode")
                .define("blockJeiFullCategoryBatchEncode", false);
        JEI_BULK_ENCODE_MAX_PATTERNS_PER_SESSION = BUILDER
                .comment("Upper bound per bulk session (-1 disables the cap). For a positive limit use 8–16384.",
                        "When limited, extras are skipped; see per-packet path for streamed single sends.")
                .translation("ae2utility.serverConfig.jeiBulkEncodeMaxPatternsPerSession")
                .defineInRange("jeiBulkEncodeMaxPatternsPerSession", 1024, -1, 16384);
        SPEC = BUILDER.build();
    }

    private Ae2UtilityServerConfig() {
    }

    public static boolean requireOpenPatternEncodingMenuForJei() {
        return REQUIRE_OPEN_PATTERN_ENCODING_MENU_FOR_JEI.get();
    }

    public static boolean blockJeiFullCategoryBatchEncode() {
        return BLOCK_JEI_FULL_CATEGORY_BATCH_ENCODE.get();
    }

    public static int jeiBulkEncodeMaxPatternsPerSession() {
        return JEI_BULK_ENCODE_MAX_PATTERNS_PER_SESSION.get();
    }
}
