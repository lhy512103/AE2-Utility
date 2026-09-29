package com.lhy.ae2utility.jei;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEKey;
import com.lhy.ae2utility.compat.WcwtCompat;
import com.lhy.ae2utility.network.CraftableStatePacket;
import com.lhy.ae2utility.network.ModNetworking;
import com.lhy.ae2utility.network.NetworkValidation;
import com.lhy.ae2utility.network.QueryCraftableStatePacket;

import appeng.menu.me.common.MEStorageMenu;

import io.netty.buffer.Unpooled;

/**
 * 客户端缓存 ME 网络「是否可合成/是否有样板」的查询结果，批量向服务器请求以降低负载。
 */
public final class CraftableStateCache {
    private static final long BATCH_SEND_INTERVAL_MS = 50L;
    private static final long CACHE_TTL_MS = 5000L;

    private static volatile long cacheVersion;

    private static final Map<AEKey, CacheEntry> CACHE = new HashMap<>();
    private static final Set<AEKey> PENDING_REQUESTS = new HashSet<>();
    private static long lastRequestTime = 0L;

    private CraftableStateCache() {}

    public static boolean isCraftable(AEKey key) {
        if (key == null) {
            return false;
        }
        if (!isKeySafeForNetworkQuery(key)) {
            return false;
        }

        if (FMLEnvironment.dist == Dist.CLIENT) {
            Boolean fromClientRepo = lookupCraftableFromPatternTerminalClientRepo(key);
            if (fromClientRepo != null) {
                return fromClientRepo;
            }
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

    private static @Nullable Boolean lookupCraftableFromPatternTerminalClientRepo(AEKey key) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(player.containerMenu instanceof MEStorageMenu menu)) {
            return null;
        }
        if (!WcwtCompat.isStorageMenu(menu)) {
            return null;
        }
        if (menu.getClientRepo() == null) {
            return null;
        }
        return ClientRepoCraftableIndex.isCraftableOutput(menu, key);
    }

    private static void requestKey(AEKey key) {
        if (!isKeySafeForNetworkQuery(key)) {
            return;
        }
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
            List<AEKey> batch = new ArrayList<>();
            int batchBytes = 5;
            for (AEKey key : PENDING_REQUESTS) {
                int keyBytes = networkSize(key);
                if (keyBytes < 0) {
                    continue;
                }
                if (!batch.isEmpty() && (batchBytes + keyBytes > NetworkValidation.MAX_SERVERBOUND_PAYLOAD_BYTES
                        || batch.size() >= NetworkValidation.MAX_CRAFTABLE_KEYS)) {
                    ModNetworking.sendToServer(new QueryCraftableStatePacket(batch));
                    batch = new ArrayList<>();
                    batchBytes = 5;
                }
                batch.add(key);
                batchBytes += keyBytes;
            }
            PENDING_REQUESTS.clear();
            if (batch.isEmpty()) {
                return;
            }
            lastRequestTime = now;
            ModNetworking.sendToServer(new QueryCraftableStatePacket(batch));
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
        cacheVersion++;
    }

    public static void invalidateKeys(Iterable<AEKey> keys) {
        ClientRepoCraftableIndex.invalidate();
        for (AEKey key : keys) {
            if (key == null) {
                continue;
            }
            CACHE.remove(key);
            PENDING_REQUESTS.remove(key);
        }
        cacheVersion++;
    }

    public static long cacheVersion() {
        return cacheVersion;
    }

    private static boolean isKeySafeForNetworkQuery(AEKey key) {
        return networkSize(key) >= 0;
    }

    private static int networkSize(AEKey key) {
        if (key == null || Minecraft.getInstance().level == null) {
            return -1;
        }
        FriendlyByteBuf buffer = null;
        try {
            buffer = new FriendlyByteBuf(Unpooled.buffer());
            AEKey.writeKey(buffer, key);
            int size = buffer.readableBytes();
            return size > NetworkValidation.MAX_SERVERBOUND_PAYLOAD_BYTES / 2 ? -1 : size;
        } catch (RuntimeException ex) {
            return -1;
        } finally {
            if (buffer != null) {
                buffer.release();
            }
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
