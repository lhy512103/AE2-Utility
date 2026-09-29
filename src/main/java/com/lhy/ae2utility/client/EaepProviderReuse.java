package com.lhy.ae2utility.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.screens.Screen;

import com.lhy.ae2utility.jei.JeiBatchEncodeQueue;
import com.lhy.ae2utility.mixin.eaep.ProviderSelectScreenAccessor;

/**
 * JEI 批量 Shift 上传期间：按搜索关键字记住本批次选过的供应器名称，
 * 同一关键字再次匹配到多个供应器时自动选同名供应器。按名称匹配，不受列表顺序变化影响。
 */
public final class EaepProviderReuse {
    private static final Map<String, String> REMEMBERED = new HashMap<>();

    private EaepProviderReuse() {
    }

    public static void reset() {
        REMEMBERED.clear();
    }

    private static boolean active() {
        return Ae2UtilityClientConfig.reuseProviderWithinBatch() && JeiBatchEncodeQueue.isActive();
    }

    public static void record(@Nullable String query, @Nullable String providerName) {
        if (active() && query != null && !query.isBlank() && providerName != null) {
            REMEMBERED.put(query, providerName);
        }
    }

    public static boolean rememberedButAbsent(@Nullable String query, List<String> names) {
        if (!active() || query == null || query.isBlank()) {
            return false;
        }
        String remembered = REMEMBERED.get(query);
        return remembered != null && !names.contains(remembered);
    }

    public static void tryAutoChoose(Screen screen) {
        if (!active() || !EaepProviderScreenLabel.isProviderSelectScreen(screen)) {
            return;
        }
        try {
            if (!ProviderSelectScreenAccessor.ae2utility$autoUploadUniqueMatchEnabled()) {
                return;
            }
            ProviderSelectScreenAccessor access = (ProviderSelectScreenAccessor) screen;
            String query = access.ae2utility$query();
            String remembered = query == null || query.isBlank() ? null : REMEMBERED.get(query);
            if (remembered == null) {
                return;
            }
            int idx = access.ae2utility$filteredNames().indexOf(remembered);
            if (idx >= 0) {
                access.ae2utility$choose(idx, true);
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
    }
}
