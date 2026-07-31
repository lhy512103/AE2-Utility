package com.lhy.ae2utility.integration.ae2;

import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;

/**
 * AE2 {@link SlotSemantics} 扩展：撕裂卡槽需参与 {@code semanticBySlot}，否则 quickMove 等路径可能异常。
 */
public final class Ae2UtilitySlotSemantics {
    /** AE2U 功能卡专用路由，避免与其它模组共享 {@link SlotSemantics#UPGRADE} 候选池。 */
    public static final SlotSemantic FEATURE_CARD =
            SlotSemantics.register("ae2utility_nbt_tear", false, 100);

    /** @deprecated 使用 {@link #FEATURE_CARD}。保留别名以兼容已有内部集成。 */
    @Deprecated(forRemoval = false)
    public static final SlotSemantic NBT_TEAR = FEATURE_CARD;

    private Ae2UtilitySlotSemantics() {
    }

    /** 在模组构造最早调用，触发类加载并完成 {@link #NBT_TEAR} 的 {@link SlotSemantics#register}。 */
    public static void bootstrap() {
        // no-op：调用方引用本类即完成 static 初始化
    }
}
