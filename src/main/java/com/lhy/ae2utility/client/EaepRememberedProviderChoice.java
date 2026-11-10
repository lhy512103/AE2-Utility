package com.lhy.ae2utility.client;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

/**
 * 「同一批量上传会话」内记忆用户为某个搜索关键字所选择的供应器<strong>名称</strong>。
 *
 * <p>与早期「服务端按 providerId 记忆」的实现不同：这里只记名称，由 mixin 在
 * {@code ProviderSelectScreen} 打开时按当前列表的名称重新匹配出对应索引，因此供应器列表
 * 顺序/可见性变化都不会导致上传到错误位置。会话结束（开始新的上传会话）时清空。</p>
 */
public final class EaepRememberedProviderChoice {
    private static final Map<String, String> REMEMBERED_BY_SEARCH_KEY = new ConcurrentHashMap<>();

    private EaepRememberedProviderChoice() {
    }

    private static String normalizeKey(String searchKey) {
        return searchKey.trim().toLowerCase(Locale.ROOT);
    }

    /** 记录某搜索关键字下被选择的供应器名称。 */
    public static void record(@Nullable String searchKey, @Nullable String providerName) {
        if (searchKey == null || searchKey.isBlank() || providerName == null || providerName.isBlank()) {
            return;
        }
        REMEMBERED_BY_SEARCH_KEY.put(normalizeKey(searchKey), providerName);
    }

    /** 取出某搜索关键字记忆的供应器名称；无则返回 {@code null}。 */
    public static @Nullable String lookup(@Nullable String searchKey) {
        if (searchKey == null || searchKey.isBlank()) {
            return null;
        }
        return REMEMBERED_BY_SEARCH_KEY.get(normalizeKey(searchKey));
    }

    /** 开始新的上传会话时清空，避免跨会话误复用。 */
    public static void forget() {
        REMEMBERED_BY_SEARCH_KEY.clear();
    }
}
