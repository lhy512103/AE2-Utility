package com.lhy.ae2utility.integration.ae2;

import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;

/**
 * AE2 {@link SlotSemantics} 扩展：撕裂卡槽需参与 {@code semanticBySlot}，否则 quickMove 等路径可能异常。
 */
public final class Ae2UtilitySlotSemantics {
    /**
     * 与 {@link SlotSemantics#register} 约定一致：模组前缀 + 语义名。
     */
    public static final SlotSemantic NBT_TEAR = SlotSemantics.register("ae2utility_nbt_tear", false, 0);

    private Ae2UtilitySlotSemantics() {
    }

    /** 在模组构造最早调用，触发类加载并完成 {@link #NBT_TEAR} 的 {@link SlotSemantics#register}。 */
    public static void bootstrap() {
        // no-op：调用方引用本类即完成 static 初始化
    }
}
