package com.lhy.ae2utility.jei;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.stacks.AEKey;
import com.lhy.ae2utility.network.CraftableStatePacket;
import com.lhy.ae2utility.network.QueryCraftableStatePacket;

/**
 * 客户端缓存 ME 网络中“是否可合成/是否有样板”的查询结果，批量向服务器请求以降低负载。
 */
public final class CraftableStateCache {
    private static final long BATCH_SEND_INTERVAL_MS = 50L;
    private static final long CACHE_TTL_MS = 5000L;

    private static final Map<AEKey, CacheEntry> CACHE = new HashMap<>();
    private static final Set<AEKey> PENDING_REQUESTS = new HashSet<>();
    private static long lastRequestTime = 0L;

    private CraftableStateCache() {}

    public static boolean isCraftable(AEKey key) {
        if (key == null) {
            return false;
        }

        CacheEntry entry = CACHE.get(key);
        long now = System.currentTimeMillis();

        if (entry != null) {
            if (now - entry.timestamp > CACHE_TTL_MS) {
                requestKey(key);
            }
            return entry.craftable;
        }

        requestKey(key);
        return false;
    }

    private static void requestKey(AEKey key) {
        long now = System.currentTimeMillis();
        PENDING_REQUESTS.add(key);
        CACHE.put(key, new CacheEntry(false, now));
    }

    public static void tick() {
        if (PENDING_REQUESTS.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastRequestTime >= BATCH_SEND_INTERVAL_MS) {
            List<AEKey> keysToRequest = new ArrayList<>(PENDING_REQUESTS);
            PENDING_REQUESTS.clear();
            lastRequestTime = now;
            PacketDistributor.sendToServer(new QueryCraftableStatePacket(keysToRequest));
        }
    }

    public static void handle(CraftableStatePacket packet) {
        long now = System.currentTimeMillis();
        for (AEKey key : packet.craftableKeys()) {
            CACHE.put(key, new CacheEntry(true, now));
        }
        for (AEKey key : packet.uncraftableKeys()) {
            CACHE.put(key, new CacheEntry(false, now));
        }
    }

    /** 丢弃缓存与待发队列中的条目，使下一帧起重新向服务器查询（用于样板写入网络后刷新 JEI 按钮状态）。 */
    public static void invalidateKeys(Iterable<AEKey> keys) {
        for (AEKey key : keys) {
            if (key == null) {
                continue;
            }
            CACHE.remove(key);
            PENDING_REQUESTS.remove(key);
        }
    }

    private static class CacheEntry {
        final boolean craftable;
        final long timestamp;

        CacheEntry(boolean craftable, long timestamp) {
            this.craftable = craftable;
            this.timestamp = timestamp;
        }
    }
}
