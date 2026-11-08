package com.lhy.ae2utility.integration.eaep;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGrid;

import com.lhy.ae2utility.network.codec.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.ModList;
import com.lhy.ae2utility.network.ModNetworking;

/**
 * Centralizes ExtendedAE-Plus reflective calls. EAEP does not expose all upload helpers as a stable API.
 */
public final class EaepReflection {
    private static final String MOD_ID = "extendedae_plus";
    private static final String UPLOAD_UTIL = "com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig";
    private static final String PENDING_UTIL = "com.extendedae_plus.util.uploadPattern.ProviderUploadUtil";
    private static final String REQUEST_PROVIDERS_PACKET = "com.extendedae_plus.network.provider.RequestProvidersListC2SPacket";

    private EaepReflection() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Stores the attribution in the same custom-data field used by ExtendedAE-Plus.
     * Keeping this on every encoded pattern also makes the value survive inventory,
     * matrix, and provider upload paths even when EAEP is loaded after the pattern is created.
     */
    public static void writeEncoderAttribution(ServerPlayer player, ItemStack pattern) {
        if (pattern.isEmpty()) {
            return;
        }
        pattern.getOrCreateTag().putString("encodePlayer", player.getGameProfile().getName());
    }

    public static @Nullable String defaultCraftingSearchKey() {
        try {
            Field field = Class.forName(UPLOAD_UTIL).getField("DEFAULT_CRAFTING_SEARCH_KEY");
            Object value = field.get(null);
            return value instanceof String s ? s : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static @Nullable String mapRecipeTypeToSearchKey(Recipe<?> recipe) {
        try {
            Method method = Class.forName(UPLOAD_UTIL).getMethod("mapRecipeTypeToSearchKey", Recipe.class);
            Object value = method.invoke(null, recipe);
            return value instanceof String s && !s.isEmpty() ? s : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static @Nullable String deriveSearchKeyFromUnknownRecipe(Object recipe) {
        try {
            Method method = Class.forName(UPLOAD_UTIL).getMethod("deriveSearchKeyFromUnknownRecipe", Object.class);
            Object value = method.invoke(null, recipe);
            return value instanceof String s && !s.isEmpty() ? s : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static @Nullable String resolveSearchKeyAlias(String raw) {
        try {
            Method method = Class.forName(UPLOAD_UTIL).getMethod("resolveSearchKeyAlias", String.class);
            Object value = method.invoke(null, raw);
            return value instanceof String s ? s : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean setLastProviderSearchKey(String key) {
        try {
            Method method = Class.forName(UPLOAD_UTIL).getMethod("setLastProviderSearchKey", String.class);
            method.invoke(null, key);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static @Nullable IGrid findPlayerGrid(ServerPlayer player) {
        try {
            Method method = Class.forName(PENDING_UTIL).getDeclaredMethod("findPlayerGrid", ServerPlayer.class);
            method.setAccessible(true);
            Object value = method.invoke(null, player);
            return value instanceof IGrid grid ? grid : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean matrixContainsPattern(IGrid grid, ItemStack pattern) {
        try {
            Class<?> utility = Class.forName("com.extendedae_plus.util.uploadPattern.MatrixUploadUtil");
            Method inventories = utility.getDeclaredMethod("findAllMatrixPatternInventories", IGrid.class);
            inventories.setAccessible(true);
            Method method = utility.getDeclaredMethod("matrixContainsPattern", java.util.List.class, ItemStack.class);
            method.setAccessible(true);
            return Boolean.TRUE.equals(method.invoke(null, inventories.invoke(null, grid), pattern));
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean uploadPatternToMatrix(ServerPlayer player, ItemStack pattern, IGrid grid) {
        try {
            Method method = Class.forName("com.extendedae_plus.util.uploadPattern.MatrixUploadUtil").getMethod("uploadPatternToMatrix", ServerPlayer.class, ItemStack.class, IGrid.class);
            Object value = method.invoke(null, player, pattern, grid);
            return !(value instanceof Boolean b) || b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean returnPendingCtrlQPatternToInventory(ServerPlayer player) {
        try {
            Method method = Class.forName(PENDING_UTIL).getMethod("returnPendingCtrlQPatternToInventory", ServerPlayer.class);
            method.invoke(null, player);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean clearPendingCtrlQUpload(ServerPlayer player) {
        try {
            Method method = Class.forName(PENDING_UTIL).getMethod("clearPendingCtrlQUpload", ServerPlayer.class);
            method.invoke(null, player);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean beginPendingCtrlQUpload(ServerPlayer player, ItemStack pattern) {
        try {
            Method method = Class.forName(PENDING_UTIL).getMethod("beginPendingCtrlQUpload", ServerPlayer.class, ItemStack.class);
            method.invoke(null, player, pattern);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean uploadPendingCtrlQPattern(ServerPlayer player, long providerId) {
        try {
            Method method = Class.forName(PENDING_UTIL).getMethod("uploadPendingCtrlQPattern", ServerPlayer.class, long.class);
            Object value = method.invoke(null, player, providerId);
            return value instanceof Enum<?> outcome && "SUCCESS".equals(outcome.name());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean requestProvidersListFromClient() {
        if (!isLoaded()) {
            return false;
        }
        try {
            Class<?> cls = Class.forName(REQUEST_PROVIDERS_PACKET);
            Object packet = cls.getDeclaredConstructor().newInstance();
            var channel = (net.minecraftforge.network.simple.SimpleChannel)Class.forName("com.extendedae_plus.init.ModNetwork").getField("CHANNEL").get(null);
            channel.sendToServer(packet);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void recordProviderUpload(ServerPlayer player, long providerId,
            appeng.helpers.patternprovider.PatternContainer container, int slot) {
        // Later EAEP releases expose undo bookkeeping; 1.5.4.1 performs its own upload bookkeeping.
        try {
            var method = Class.forName(PENDING_UTIL).getMethod("recordProviderUpload", ServerPlayer.class,
                    long.class, appeng.helpers.patternprovider.PatternContainer.class, int.class);
            method.invoke(null, player, providerId, container, slot);
        } catch (ReflectiveOperationException ignored) { }
    }
}
