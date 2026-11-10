package com.lhy.ae2utility.integration.eaep;

import java.lang.reflect.Method;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Version-neutral access to the legacy monolith and EAEP 1.6 split upload helpers. */
public final class EaepReflection {
    private static final String MOD_ID = "extendedae_plus";
    private static final String PENDING_UTIL = "com.extendedae_plus.util.uploadPattern.CtrlQPendingUploadUtil";
    private static final String REQUEST_PROVIDERS_PACKET = "com.extendedae_plus.network.RequestProvidersListC2SPacket";

    private EaepReflection() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static void writeEncoderAttribution(ServerPlayer player, ItemStack pattern) {
        if (!pattern.isEmpty()) {
            CustomData.update(DataComponents.CUSTOM_DATA, pattern,
                    tag -> tag.putString("encodePlayer", player.getGameProfile().name()));
        }
    }

    public static @Nullable String defaultCraftingSearchKey() {
        try {
            Object value = EaepApiAccess.uploadField("RecipeTypeNameConfig", "DEFAULT_CRAFTING_SEARCH_KEY");
            return value instanceof String s ? s : null;
        } catch (ReflectiveOperationException unavailable) {
            return null;
        }
    }

    public static @Nullable String mapRecipeTypeToSearchKey(Recipe<?> recipe) {
        return searchKey("mapRecipeTypeToSearchKey", Recipe.class, recipe);
    }

    public static @Nullable String deriveSearchKeyFromUnknownRecipe(Object recipe) {
        return searchKey("deriveSearchKeyFromUnknownRecipe", Object.class, recipe);
    }

    public static @Nullable String resolveSearchKeyAlias(String raw) {
        return searchKey("resolveSearchKeyAlias", String.class, raw);
    }

    private static @Nullable String searchKey(String method, Class<?> parameter, Object argument) {
        try {
            Object value = EaepApiAccess.invokeUpload("RecipeTypeNameConfig", method,
                    new Class<?>[] {parameter}, argument);
            return value instanceof String s ? s : null;
        } catch (ReflectiveOperationException unavailable) {
            return null;
        }
    }

    public static boolean setLastProviderSearchKey(String key) {
        try {
            EaepApiAccess.invokeUpload("RecipeTypeNameConfig", "setLastProviderSearchKey",
                    new Class<?>[] {String.class}, key);
            return true;
        } catch (ReflectiveOperationException unavailable) {
            return false;
        }
    }

    private static Object invokePending(String name, Class<?>[] parameters, Object... arguments)
            throws ReflectiveOperationException {
        Method method = Class.forName(PENDING_UTIL).getMethod(name, parameters);
        return method.invoke(null, arguments);
    }

    public static @Nullable IGrid findPlayerGrid(ServerPlayer player) {
        try {
            Object value = invokePending("findPlayerGrid", new Class<?>[] {ServerPlayer.class}, player);
            return value instanceof IGrid grid ? grid : null;
        } catch (ReflectiveOperationException unavailable) {
            return null;
        }
    }

    public static boolean matrixContainsPattern(IGrid grid, ItemStack pattern) {
        try {
            return Boolean.TRUE.equals(EaepApiAccess.invokeUpload("MatrixUploadUtil", "matrixContainsPattern",
                    new Class<?>[] {IGrid.class, ItemStack.class}, grid, pattern));
        } catch (ReflectiveOperationException unavailable) {
            return false;
        }
    }

    public static boolean uploadPatternToMatrix(ServerPlayer player, ItemStack pattern, IGrid grid) {
        return Boolean.TRUE.equals(invokeRequiredUpload("MatrixUploadUtil", "uploadPatternToMatrix",
                new Class<?>[] {ServerPlayer.class, ItemStack.class, IGrid.class}, player, pattern, grid));
    }

    public static boolean uploadPatternToProvider(ServerPlayer player, int slot, long providerId) {
        return Boolean.TRUE.equals(invokeRequiredUpload("ProviderUploadUtil", "uploadPatternToProvider",
                new Class<?>[] {ServerPlayer.class, int.class, long.class}, player, slot, providerId));
    }

    public static List<PatternContainer> listAvailableProvidersFromGrid(IGrid grid) {
        Object value = invokeRequiredUpload("ProviderUploadUtil", "listAvailableProvidersFromGrid",
                new Class<?>[] {IGrid.class}, grid);
        if (!(value instanceof List<?> providers)) {
            throw new IllegalStateException("EAEP returned a non-list provider inventory");
        }
        return providers.stream().map(PatternContainer.class::cast).toList();
    }

    public static void recordProviderUpload(ServerPlayer player, long id, PatternContainer container, int slot) {
        invokeRequiredUpload("ProviderUploadUtil", "recordProviderUpload",
                new Class<?>[] {ServerPlayer.class, long.class, PatternContainer.class, int.class},
                player, id, container, slot);
    }

    private static Object invokeRequiredUpload(String role, String name, Class<?>[] parameters, Object... arguments) {
        try {
            return EaepApiAccess.invokeUpload(role, name, parameters, arguments);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot call EAEP " + role + "." + name, failure);
        }
    }

    public static boolean returnPendingCtrlQPatternToInventory(ServerPlayer player) {
        try {
            return Boolean.TRUE.equals(invokePending("returnPendingCtrlQPatternToInventory",
                    new Class<?>[] {ServerPlayer.class}, player));
        } catch (ReflectiveOperationException unavailable) {
            return false;
        }
    }

    public static boolean clearPendingCtrlQUpload(ServerPlayer player) {
        try {
            invokePending("clearPendingCtrlQUpload", new Class<?>[] {ServerPlayer.class}, player);
            return true;
        } catch (ReflectiveOperationException unavailable) {
            return false;
        }
    }

    public static boolean beginPendingCtrlQUpload(ServerPlayer player, ItemStack pattern) {
        try {
            Object id = invokePending("beginPendingCtrlQUpload",
                    new Class<?>[] {ServerPlayer.class, ItemStack.class}, player, pattern);
            return id instanceof String text && !text.isBlank();
        } catch (ReflectiveOperationException unavailable) {
            return false;
        }
    }

    public static boolean uploadPendingCtrlQPattern(ServerPlayer player, long providerId) {
        try {
            Object result = invokePending("uploadPendingCtrlQPattern",
                    new Class<?>[] {ServerPlayer.class, long.class}, player, providerId);
            return EaepApiAccess.uploadResult(result) == EaepApiAccess.UploadResult.SUCCESS;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot upload EAEP pending pattern", failure);
        }
    }

    public static boolean requestProvidersListFromClient() {
        if (!isLoaded()) {
            return false;
        }
        try {
            Object packet = Class.forName(REQUEST_PROVIDERS_PACKET).getDeclaredField("INSTANCE").get(null);
            ClientPacketDistributor.sendToServer((CustomPacketPayload) packet);
            return true;
        } catch (ReflectiveOperationException unavailable) {
            return false;
        }
    }
}
