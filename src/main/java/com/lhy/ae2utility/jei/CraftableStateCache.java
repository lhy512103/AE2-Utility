package com.lhy.ae2utility.jei;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEKey;
import appeng.menu.me.common.MEStorageMenu;
import com.lhy.ae2utility.client.ClientCacheLifecycle;
import com.lhy.ae2utility.client.cache.ClientRequestCache;
import com.lhy.ae2utility.compat.WcwtCompat;
import com.lhy.ae2utility.network.CraftableStatePacket;
import com.lhy.ae2utility.network.ModNetworking;
import com.lhy.ae2utility.network.NetworkValidation;
import com.lhy.ae2utility.network.QueryCraftableStatePacket;

/** AEKey-keyed, world/player/menu-scoped craftability. 4096 LRU entries, 5s TTL/retry;
 * at most one bounded batch per client tick and 50ms. Terminal lookups share the repository index.
 */
public final class CraftableStateCache {
    private static final ClientRequestCache<AEKey, Boolean> CACHE =
            new ClientRequestCache<>(4096, 5000, 5000, ClientRequestCache::monotonicMillis);
    private static final Set<AEKey> PENDING_REQUESTS = new LinkedHashSet<>();
    private static long lastRequestTime;
    private static boolean existingPatternCachesStale;
    private static long cacheVersion;

    private CraftableStateCache() {}

    public static boolean isCraftable(AEKey key) {
        ClientCacheLifecycle.synchronizeContext();
        if (key == null || Minecraft.getInstance().level == null) return false;
        Boolean fromRepo = lookupCraftableFromPatternTerminalClientRepo(key);
        if (fromRepo != null) return fromRepo;
        Boolean value = CACHE.get(key);
        if (CACHE.shouldRequest(key) && PENDING_REQUESTS.size() < NetworkValidation.MAX_CRAFTABLE_KEYS) {
            PENDING_REQUESTS.add(key);
        }
        return Boolean.TRUE.equals(value);
    }

    private static @Nullable Boolean lookupCraftableFromPatternTerminalClientRepo(AEKey key) {
        var player = Minecraft.getInstance().player;
        if (player == null || !(player.containerMenu instanceof MEStorageMenu menu)
                || !WcwtCompat.isPatternEncodingLikeMenu(menu) || menu.getClientRepo() == null) return null;
        return ClientRepoCraftableIndex.isCraftableOutput(menu, key);
    }

    public static void tick() {
        if (PENDING_REQUESTS.isEmpty() || Minecraft.getInstance().level == null) return;
        long now = ClientRequestCache.monotonicMillis();
        if (now - lastRequestTime < 50) return;
        var keys = new ArrayList<AEKey>(PENDING_REQUESTS.size());
        for (AEKey key : PENDING_REQUESTS) {
            if (CACHE.shouldRequest(key) && isKeySafeForNetworkQuery(key)) keys.add(key);
        }
        PENDING_REQUESTS.clear();
        if (keys.isEmpty()) return;
        long nonce = ClientRequestCache.nextNonce();
        for (AEKey key : keys) CACHE.beginRequest(key, nonce);
        lastRequestTime = now;
        ModNetworking.sendToServer(new QueryCraftableStatePacket(nonce, keys));
    }

    public static void handle(CraftableStatePacket packet) {
        ClientCacheLifecycle.synchronizeContext();
        boolean changed = false;
        for (AEKey key : packet.craftableKeys()) changed |= CACHE.complete(key, packet.requestNonce(), true);
        for (AEKey key : packet.uncraftableKeys()) changed |= CACHE.complete(key, packet.requestNonce(), false);
        if (changed) markChanged();
    }

    public static void invalidateKeys(Iterable<AEKey> keys) {
        ClientRepoCraftableIndex.invalidate();
        for (AEKey key : keys) {
            CACHE.remove(key);
            PENDING_REQUESTS.remove(key);
        }
        markChanged();
    }

    public static void clear() {
        CACHE.clear();
        PENDING_REQUESTS.clear();
        // Keep the send throttle and the global nonce monotonic across lifecycle changes.
        markChanged();
    }

    private static void markChanged() { existingPatternCachesStale = true; cacheVersion++; }
    public static long cacheVersion() { return cacheVersion; }
    public static boolean pollExistingPatternCachesStale() {
        boolean stale = existingPatternCachesStale;
        existingPatternCachesStale = false;
        return stale;
    }

    private static boolean isKeySafeForNetworkQuery(AEKey key) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AEKey.writeKey(buffer, key);
            return true;
        } catch (RuntimeException ex) {
            return false;
        } finally {
            buffer.release();
        }
    }
}
