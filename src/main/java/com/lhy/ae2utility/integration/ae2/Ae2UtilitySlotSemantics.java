package com.lhy.ae2utility.integration.ae2;

import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;

public final class Ae2UtilitySlotSemantics {
    public static final SlotSemantic FEATURE_CARD = SlotSemantics.register("ae2utility_nbt_tear", false);

    @Deprecated
    public static final SlotSemantic NBT_TEAR = FEATURE_CARD;

    private Ae2UtilitySlotSemantics() {
    }

    public static void bootstrap() {
        // class-load register
    }
}
