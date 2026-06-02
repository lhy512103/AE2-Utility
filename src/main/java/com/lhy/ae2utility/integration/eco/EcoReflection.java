package com.lhy.ae2utility.integration.eco;

import java.lang.reflect.Method;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGrid;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Soft-compat bridge to Neo ECO AE Extension ({@code neoecoae}).
 *
 * <p>ECO exposes a grid service {@code IECOPatternStorageService} whose
 * {@code getPatternStorage().insertPattern(ItemStack)} stores a crafting-style encoded
 * pattern into its dedicated crafting subsystem (and returns {@code false} when full).
 * We reach it purely by reflection so ECO stays an optional dependency.</p>
 */
public final class EcoReflection {
    private static final String MOD_ID = "neoecoae";
    private static final String SERVICE = "cn.dancingsnow.neoecoae.api.IECOPatternStorageService";
    private static final String STORAGE = "cn.dancingsnow.neoecoae.api.IECOPatternStorage";

    private EcoReflection() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** @return true if the grid currently exposes an ECO pattern-storage subsystem. */
    public static boolean networkHasEcoPatternStorage(@Nullable IGrid grid) {
        return resolveStorage(grid) != null;
    }

    /**
     * Tries to insert a crafting/smithing/stonecutter encoded pattern into ECO's crafting
     * subsystem.
     *
     * @return {@code true} only when ECO accepted the pattern; {@code false} when ECO is
     *         absent, has no storage on this grid, or is full (caller should then fall back).
     */
    public static boolean tryInsertPattern(@Nullable IGrid grid, @Nullable ItemStack pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }
        Object storage = resolveStorage(grid);
        if (storage == null) {
            return false;
        }
        try {
            Method insert = Class.forName(STORAGE).getMethod("insertPattern", ItemStack.class);
            Object result = insert.invoke(storage, pattern.copy());
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static @Nullable Object resolveStorage(@Nullable IGrid grid) {
        if (grid == null || !isLoaded()) {
            return null;
        }
        try {
            Class<?> serviceCls = Class.forName(SERVICE);
            Method getService = IGrid.class.getMethod("getService", Class.class);
            Object service = getService.invoke(grid, serviceCls);
            if (service == null) {
                return null;
            }
            Method getStorage = serviceCls.getMethod("getPatternStorage");
            return getStorage.invoke(service);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
