package com.lhy.ae2utility;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 逻辑服务端配置（生成 {@code serverconfig/ae2utility-server.toml}），在专用服与单机存档主机侧生效。
 */
public final class Ae2UtilityServerConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue REQUIRE_OPEN_PATTERN_ENCODING_MENU_FOR_JEI;
    public static final ForgeConfigSpec.BooleanValue BLOCK_JEI_FULL_CATEGORY_BATCH_ENCODE;
    public static final ForgeConfigSpec.IntValue JEI_BULK_ENCODE_MAX_PATTERNS_PER_SESSION;
    public static final ForgeConfigSpec.DoubleValue ME_QUICK_TRANSFER_GROUP_ENERGY;
    public static final ForgeConfigSpec.DoubleValue ME_QUICK_TRANSFER_ALL_ENERGY;

    static {
        BUILDER.comment("Pattern encoding policy for dedicated servers / singleplayer host.");
        REQUIRE_OPEN_PATTERN_ENCODING_MENU_FOR_JEI = BUILDER
                .comment("If true, reject JEI encode packets unless a pattern-encoding ME screen is open (blocks wireless-from-inventory).",
                        "Does not remove the client-side option; this rule is stricter when enabled.")
                .translation("ae2utility.serverConfig.requireOpenPatternEncodingMenuForJei")
                .define("requireOpenPatternEncodingMenuForJei", false);
        BLOCK_JEI_FULL_CATEGORY_BATCH_ENCODE = BUILDER
                .comment("If true, reject encode packets tagged as full JEI category (all pages) batch encoding.",
                        "Does not affect current-page batch or single-recipe encode.")
                .translation("ae2utility.serverConfig.blockJeiFullCategoryBatchEncode")
                .define("blockJeiFullCategoryBatchEncode", false);
        JEI_BULK_ENCODE_MAX_PATTERNS_PER_SESSION = BUILDER
                .comment("Maximum patterns encoded per JEI batch session (-1 disables the cap).",
                        "Extra requests in the same session are skipped.")
                .translation("ae2utility.serverConfig.jeiBulkEncodeMaxPatternsPerSession")
                .defineInRange("jeiBulkEncodeMaxPatternsPerSession", 1024, -1, 16384);
        BUILDER.comment("ME Quick Transfer Tool energy costs.");
        ME_QUICK_TRANSFER_GROUP_ENERGY = BUILDER
                .comment("Fixed AE energy cost for a normal right-click transfer.")
                .translation("ae2utility.serverConfig.meQuickTransferGroupEnergy")
                .defineInRange("meQuickTransferGroupEnergy", 100.0, 0.0, Double.MAX_VALUE);
        ME_QUICK_TRANSFER_ALL_ENERGY = BUILDER
                .comment("Fixed AE energy cost for a sneak right-click transfer of all available contents.")
                .translation("ae2utility.serverConfig.meQuickTransferAllEnergy")
                .defineInRange("meQuickTransferAllEnergy", 1000.0, 0.0, Double.MAX_VALUE);
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

    public static double meQuickTransferGroupEnergy() {
        return ME_QUICK_TRANSFER_GROUP_ENERGY.get();
    }

    public static double meQuickTransferAllEnergy() {
        return ME_QUICK_TRANSFER_ALL_ENERGY.get();
    }
}
