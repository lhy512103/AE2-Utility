package com.lhy.ae2utility.compat;

import net.neoforged.fml.ModList;

/**
 * 集中维护可选模组的能力探测，避免业务类散落 Mod ID 字符串。
 * 这里只做加载状态查询，不引用可选模组的类，保证缺少依赖时仍能启动。
 */
public final class ModCapabilities {
    public static final String EMI = "emi";
    public static final String JEICT = "jeict";
    public static final String EXTENDEDAE_PLUS = "extendedae_plus";
    public static final String EXTENDEDAE = "extendedae";
    public static final String AE2_CRYSTAL_SCIENCE = "ae2cs";
    public static final String ADVANCED_AE = "advanced_ae";
    public static final String AE2_LIGHTNING_TECH = "ae2lt";

    private ModCapabilities() {
    }

    public static boolean isLoaded(String modId) {
        return modId != null && ModList.get().isLoaded(modId);
    }

    public static boolean hasEmi() {
        return isLoaded(EMI);
    }

    public static boolean hasJeict() {
        return isLoaded(JEICT);
    }

    public static boolean hasExtendedAePlus() {
        return isLoaded(EXTENDEDAE_PLUS);
    }

    public static boolean hasExtendedAe() {
        return isLoaded(EXTENDEDAE);
    }

    public static boolean hasAe2CrystalScience() {
        return isLoaded(AE2_CRYSTAL_SCIENCE);
    }

    public static boolean hasAdvancedAe() {
        return isLoaded(ADVANCED_AE);
    }

    public static boolean hasAe2LightningTech() {
        return isLoaded(AE2_LIGHTNING_TECH);
    }
}
