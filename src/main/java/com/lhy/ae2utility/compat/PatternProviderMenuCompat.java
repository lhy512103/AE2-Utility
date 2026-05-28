package com.lhy.ae2utility.compat;

public final class PatternProviderMenuCompat {
    private static final String AE2_PATTERN_PROVIDER_MENU = "appeng.menu.implementations.PatternProviderMenu";
    private static final String AE2CS_UPGRADEABLE_PATTERN_PROVIDER_MENU = "io.github.lounode.ae2cs.common.menu.UpgradeablePatternProviderMenu";
    private static final String AAE_ADV_PATTERN_PROVIDER_MENU = "net.pedroksl.advanced_ae.gui.advpatternprovider.AdvPatternProviderMenu";
    private static final String AAE_SMALL_ADV_PATTERN_PROVIDER_MENU = "net.pedroksl.advanced_ae.gui.advpatternprovider.SmallAdvPatternProviderMenu";
    private static final String AE2LT_OVERLOADED_PATTERN_PROVIDER_MENU = "com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu";

    private PatternProviderMenuCompat() {
    }

    public static boolean isSupportedPatternProviderMenu(Object menu) {
        if (menu == null) {
            return false;
        }
        for (Class<?> type = menu.getClass(); type != null; type = type.getSuperclass()) {
            String name = type.getName();
            if (AE2_PATTERN_PROVIDER_MENU.equals(name)
                    || AE2CS_UPGRADEABLE_PATTERN_PROVIDER_MENU.equals(name)
                    || AAE_ADV_PATTERN_PROVIDER_MENU.equals(name)
                    || AAE_SMALL_ADV_PATTERN_PROVIDER_MENU.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isOverloadedPatternProviderMenu(Object menu) {
        if (menu == null) {
            return false;
        }
        for (Class<?> type = menu.getClass(); type != null; type = type.getSuperclass()) {
            if (AE2LT_OVERLOADED_PATTERN_PROVIDER_MENU.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }
}
