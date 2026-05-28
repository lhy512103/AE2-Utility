package com.lhy.ae2utility.service;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import appeng.api.networking.IGrid;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public final class RecipeTreeUploadContextBridge {
    private static final Map<UUID, IGrid> PENDING_GRIDS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> PENDING_SEARCH_KEYS = new ConcurrentHashMap<>();
    /** 与当前待定上传对应的 JEI/配方树「机器显示名」（subtitle），用于在检索键不一致时仍能匹配已记住的供应器 */
    private static final Map<UUID, String> PENDING_PROVIDER_DISPLAY_NAMES = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Long>> REMEMBERED_PROVIDER_IDS = new ConcurrentHashMap<>();

    private RecipeTreeUploadContextBridge() {
    }

    public static void rememberGrid(ServerPlayer player, @Nullable IGrid grid) {
        if (player == null) {
            return;
        }
        if (grid == null) {
            PENDING_GRIDS.remove(player.getUUID());
            return;
        }
        PENDING_GRIDS.put(player.getUUID(), grid);
    }

    public static @Nullable IGrid getRememberedGrid(ServerPlayer player) {
        return player == null ? null : PENDING_GRIDS.get(player.getUUID());
    }

    public static void rememberPendingSearchKey(ServerPlayer player, @Nullable String searchKey) {
        if (player == null) {
            return;
        }
        if (searchKey == null || searchKey.isBlank()) {
            PENDING_SEARCH_KEYS.remove(player.getUUID());
            return;
        }
        PENDING_SEARCH_KEYS.put(player.getUUID(), searchKey);
    }

    public static @Nullable String getPendingSearchKey(ServerPlayer player) {
        return player == null ? null : PENDING_SEARCH_KEYS.get(player.getUUID());
    }

    public static void rememberPendingProviderDisplayName(ServerPlayer player, @Nullable String machineDisplayName) {
        if (player == null) {
            return;
        }
        if (machineDisplayName == null || machineDisplayName.isBlank()) {
            PENDING_PROVIDER_DISPLAY_NAMES.remove(player.getUUID());
            return;
        }
        PENDING_PROVIDER_DISPLAY_NAMES.put(player.getUUID(), machineDisplayName.trim());
    }

    public static @Nullable String getPendingProviderDisplayName(ServerPlayer player) {
        return player == null ? null : PENDING_PROVIDER_DISPLAY_NAMES.get(player.getUUID());
    }

    private static String displayNameRememberKey(String displayName) {
        return "!dn:" + displayName.trim().toLowerCase(Locale.ROOT);
    }

    public static void rememberSuccessfulProvider(ServerPlayer player, long providerId) {
        if (player == null) {
            return;
        }
        String searchKey = getPendingSearchKey(player);
        String displayName = getPendingProviderDisplayName(player);
        boolean hasSearchKey = searchKey != null && !searchKey.isBlank();
        boolean hasDisplayName = displayName != null && !displayName.isBlank();
        if (!hasSearchKey && !hasDisplayName) {
            return;
        }
        Map<String, Long> remembered = REMEMBERED_PROVIDER_IDS.computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>());
        if (hasSearchKey) {
            remembered.put(searchKey, providerId);
        }
        if (hasDisplayName) {
            remembered.put(displayNameRememberKey(displayName), providerId);
        }
    }

    /** @param searchKey 原始 EAEP 检索键（与分包 {@code providerSearchKey}/服务端推导一致） */
    public static @Nullable Long getRememberedProviderId(ServerPlayer player, @Nullable String searchKey) {
        return getRememberedProviderId(player, searchKey, null);
    }

    /**
     * 先按检索键查找；若无则按机器显示名（配方树 subtitle / providerDisplayName）查找，缓解不同配方推导键不一致导致的重复选手。
     */
    public static @Nullable Long getRememberedProviderId(ServerPlayer player, @Nullable String searchKey,
            @Nullable String machineDisplayNameFallback) {
        if (player == null) {
            return null;
        }
        Map<String, Long> remembered = REMEMBERED_PROVIDER_IDS.get(player.getUUID());
        if (remembered == null) {
            return null;
        }
        if (searchKey != null && !searchKey.isBlank()) {
            Long id = remembered.get(searchKey);
            if (id != null) {
                return id;
            }
        }
        if (machineDisplayNameFallback != null && !machineDisplayNameFallback.isBlank()) {
            return remembered.get(displayNameRememberKey(machineDisplayNameFallback));
        }
        return null;
    }

    public static void forgetRememberedProvider(ServerPlayer player, @Nullable String searchKey) {
        forgetRememberedProvider(player, searchKey, null);
    }

    public static void forgetRememberedProvider(ServerPlayer player, @Nullable String searchKey,
            @Nullable String machineDisplayNameFallback) {
        if (player == null) {
            return;
        }
        Map<String, Long> remembered = REMEMBERED_PROVIDER_IDS.get(player.getUUID());
        if (remembered == null) {
            return;
        }
        if (searchKey != null && !searchKey.isBlank()) {
            remembered.remove(searchKey);
        }
        if (machineDisplayNameFallback != null && !machineDisplayNameFallback.isBlank()) {
            remembered.remove(displayNameRememberKey(machineDisplayNameFallback));
        }
        if (remembered.isEmpty()) {
            REMEMBERED_PROVIDER_IDS.remove(player.getUUID());
        }
    }

    public static void clear(ServerPlayer player) {
        if (player != null) {
            UUID id = player.getUUID();
            PENDING_GRIDS.remove(id);
            PENDING_SEARCH_KEYS.remove(id);
            PENDING_PROVIDER_DISPLAY_NAMES.remove(id);
        }
    }

    /** stopuploads 等强制中止：与 {@link #clear} 不同，会清空按搜索关键字记忆的供应器 id，避免误判「唯一供应器」。 */
    public static void wipeUploadOrchestrationState(@Nullable ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUUID();
        PENDING_GRIDS.remove(id);
        PENDING_SEARCH_KEYS.remove(id);
        PENDING_PROVIDER_DISPLAY_NAMES.remove(id);
        REMEMBERED_PROVIDER_IDS.remove(id);
    }
}
