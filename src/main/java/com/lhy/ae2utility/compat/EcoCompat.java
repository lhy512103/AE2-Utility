package com.lhy.ae2utility.compat;

import java.lang.reflect.Method;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridService;

/**
 * Neo ECO AE Extension（neoecoae）软依赖：把合成/锻造/切石样板直接写入 ECO 合成子系统。
 */
public final class EcoCompat {
    private static final String MOD_ID = "neoecoae";
    private static final String SERVICE = "cn.dancingsnow.neoecoae.api.IECOPatternStorageService";
    private static final String STORAGE = "cn.dancingsnow.neoecoae.api.IECOPatternStorage";

    public enum InsertResult {
        INSERTED, ALREADY_PRESENT, NOT_ACCEPTED
    }

    private EcoCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    @SuppressWarnings("unchecked")
    public static InsertResult tryInsertPattern(@Nullable IGrid grid, ItemStack pattern) {
        if (grid == null || pattern.isEmpty() || !isLoaded()) {
            return InsertResult.NOT_ACCEPTED;
        }
        try {
            Class<? extends IGridService> serviceClass = (Class<? extends IGridService>) Class.forName(SERVICE);
            Object service = grid.getService(serviceClass);
            if (service == null) {
                return InsertResult.NOT_ACCEPTED;
            }
            Object storage = serviceClass.getMethod("getPatternStorage").invoke(service);
            if (storage == null) {
                return InsertResult.NOT_ACCEPTED;
            }
            Method insert = Class.forName(STORAGE).getMethod("insertPattern", ItemStack.class);
            Object result = insert.invoke(storage, pattern.copy());
            String name = result instanceof Enum<?> e ? e.name() : String.valueOf(result);
            return switch (name) {
                case "INSERTED", "true" -> InsertResult.INSERTED;
                case "ALREADY_PRESENT" -> InsertResult.ALREADY_PRESENT;
                default -> InsertResult.NOT_ACCEPTED;
            };
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return InsertResult.NOT_ACCEPTED;
        }
    }
}
