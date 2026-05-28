package com.lhy.ae2utility.jei;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEKey;
import com.lhy.ae2utility.compat.WcwtCompat;
import com.lhy.ae2utility.network.CraftableStatePacket;
import com.lhy.ae2utility.network.QueryCraftableStatePacket;

import appeng.menu.me.common.MEStorageMenu;

/**
 * 客户端缓存 ME 网络中“是否可合成/是否有样板”的查询结果，批量向服务器请求以降低负载。
 * <p>若当前已打开 {@link WcwtCompat#isPatternEncodingLikeMenu 样板编码类} {@link MEStorageMenu} 且存在 {@link MEStorageMenu#getClientRepo()}，
 * 则 {@link #isCraftable(AEKey)} 与 AE2 JEI 拉配方预览同源，经由 {@link ClientRepoCraftableIndex} 读取终端 crafting 快照（同 tick 内共享一次快照，避免重复扫表）；不再对该键走发包查询。</p>
 */
public final class CraftableStateCache {
    private static final long BATCH_SEND_INTERVAL_MS = 50L;
    private static final long CACHE_TTL_MS = 5000L;

    /**
     * 样板可合成快照更新后置位；配方树界面在 tick 中消费并重算「所需样板数」与合并层红框缓存，避免每帧扫树。
     */
    private static volatile boolean recipeTreeOverlayCachesStale;

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

    /**
     * 与 AE2 {@link Ae2TerminalRecipeTransferHandler} 拉配方预览、{@link ClientRepoCraftableIndex} 同源：终端 crafting 快照中已标记可合成的输出键。
     *
     * @return 已打开样板 {@link MEStorageMenu} 且能取得 {@code clientRepo} 时为确定值；否则 {@code null}（走 {@link #CACHE} / 发包）。
     */
    private static @Nullable Boolean lookupCraftableFromPatternTerminalClientRepo(AEKey key) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(player.containerMenu instanceof MEStorageMenu menu)) {
            return null;
        }
        if (!WcwtCompat.isPatternEncodingLikeMenu(menu)) {
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
            List<AEKey> keysToRequest = new ArrayList<>(PENDING_REQUESTS.size());
            for (AEKey key : PENDING_REQUESTS) {
                if (isKeySafeForNetworkQuery(key)) {
                    keysToRequest.add(key);
                }
            }
            PENDING_REQUESTS.clear();
            if (keysToRequest.isEmpty()) {
                return;
            }
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
        recipeTreeOverlayCachesStale = true;
    }

    /** 丢弃缓存与待发队列中的条目，使下一帧起重新向服务器查询（用于样板写入网络后刷新 JEI 按钮状态）。 */
    public static void invalidateKeys(Iterable<AEKey> keys) {
        ClientRepoCraftableIndex.invalidate();
        for (AEKey key : keys) {
            if (key == null) {
                continue;
            }
            CACHE.remove(key);
            PENDING_REQUESTS.remove(key);
        }
        recipeTreeOverlayCachesStale = true;
    }

    /** @return true 并已清除标记（每帧至多消费一次）。 */
    public static boolean pollRecipeTreeOverlayCachesStale() {
        if (!recipeTreeOverlayCachesStale) {
            return false;
        }
        recipeTreeOverlayCachesStale = false;
        return true;
    }

    private static boolean isKeySafeForNetworkQuery(AEKey key) {
        if (key == null || Minecraft.getInstance().level == null) {
            return false;
        }
        RegistryFriendlyByteBuf buffer = null;
        try {
            buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), Minecraft.getInstance().level.registryAccess());
            AEKey.writeKey(buffer, key);
            return true;
        } catch (RuntimeException ex) {
            return false;
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
