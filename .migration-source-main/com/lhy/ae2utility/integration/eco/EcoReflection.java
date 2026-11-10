package com.lhy.ae2utility.integration.eco;

import java.lang.reflect.Method;

import org.jetbrains.annotations.Nullable;

import com.lhy.ae2utility.init.ModDataComponents;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;
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
    private static final String PATTERN_BUS = "cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity";

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
            Object result = insert.invoke(storage, copyWithoutUploadData(pattern));
            return Boolean.TRUE.equals(result);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Checks whether ECO's crafting subsystem on this grid already stores an identical
     * encoded pattern, mirroring how EAEP guards against assembly-matrix duplicates.
     *
     * <p>ECO exposes its crafting pattern buses as normal AE2 {@link PatternContainer}
     * machines, so scan those containers instead of depending on ECO's private service
     * fields.</p>
     */
    public static boolean containsPattern(@Nullable IGrid grid, @Nullable ItemStack pattern) {
        if (grid == null || pattern == null || pattern.isEmpty() || !isLoaded()) {
            return false;
        }
        ItemStack normalizedPattern = copyWithoutUploadData(pattern);
        for (Class<?> machineClass : grid.getMachineClasses()) {
            if (!PatternContainer.class.isAssignableFrom(machineClass)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Class<? extends PatternContainer> containerClass = (Class<? extends PatternContainer>) machineClass;
            for (PatternContainer container : grid.getActiveMachines(containerClass)) {
                if (container != null && isEcoPatternProvider(container)
                        && inventoryContains(container, normalizedPattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isEcoPatternProvider(PatternContainer container) {
        Class<?> cls = container.getClass();
        while (cls != null) {
            if (PATTERN_BUS.equals(cls.getName())) {
                return true;
            }
            cls = cls.getSuperclass();
        }
        return false;
    }

    private static boolean inventoryContains(PatternContainer container, ItemStack pattern) {
        InternalInventory inv = container.getTerminalPatternInventory();
        if (inv == null) {
            return false;
        }
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty() && samePatternIgnoringUploadData(stack, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean samePatternIgnoringUploadData(ItemStack first, ItemStack second) {
        return ItemStack.isSameItemSameComponents(copyWithoutUploadData(first), copyWithoutUploadData(second));
    }

    private static ItemStack copyWithoutUploadData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.remove(ModDataComponents.PATTERN_PROVIDER_SEARCH_KEY.get());
        return copy;
    }

    private static @Nullable Object resolveService(@Nullable IGrid grid) {
        if (grid == null || !isLoaded()) {
            return null;
        }
        try {
            Class<?> serviceCls = Class.forName(SERVICE);
            Method getService = IGrid.class.getMethod("getService", Class.class);
            return getService.invoke(grid, serviceCls);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable Object resolveStorage(@Nullable IGrid grid) {
        Object service = resolveService(grid);
        if (service == null) {
            return null;
        }
        try {
            Method getStorage = Class.forName(SERVICE).getMethod("getPatternStorage");
            return getStorage.invoke(service);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
