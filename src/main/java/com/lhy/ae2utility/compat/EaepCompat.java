package com.lhy.ae2utility.compat;

import net.neoforged.fml.ModList;

/**
 * ExtendedAE Plus（mod id：{@code extendedae_plus}）。供服务端与客户端共用。
 */
public final class EaepCompat {
    private EaepCompat() {
    }

    public static boolean isExtendedAePlusLoaded() {
        return ModList.get().isLoaded("extendedae_plus");
    }
}
