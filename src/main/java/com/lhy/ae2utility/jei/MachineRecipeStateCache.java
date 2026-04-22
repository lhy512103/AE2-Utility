package com.lhy.ae2utility.jei;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.neoforged.neoforge.network.PacketDistributor;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.network.MachineRecipeStatePacket;
import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;
import com.lhy.ae2utility.network.QueryMachineRecipeStatePacket;
import com.lhy.ae2utility.network.RecipeTransferPacketHelper;

public final class MachineRecipeStateCache {
    private static final long REQUEST_INTERVAL_MS = 500L;
    private static final int MAX_CACHE_ENTRIES = 64;

    private static final Map<CacheKey, CacheEntry> CACHE = new LinkedHashMap<>();

    private MachineRecipeStateCache() {
    }

    @Nullable
    public static MachineRecipeStatePacket getOrRequest(int containerId, String profileId, List<RequestedIngredient> requestedIngredients) {
        List<RequestedIngredient> copiedIngredients = copyRequestedIngredients(requestedIngredients);
        String requestKey = RecipeTransferPacketHelper.requestSignature(copiedIngredients);
        CacheKey cacheKey = new CacheKey(containerId, profileId, requestKey);
        long now = System.currentTimeMillis();
        CacheEntry entry = CACHE.get(cacheKey);
        if (entry != null && entry.packet() != null) {
            return entry.packet();
        }

        if (entry == null || now - entry.lastRequestMs() >= REQUEST_INTERVAL_MS) {
            PacketDistributor.sendToServer(new QueryMachineRecipeStatePacket(containerId, profileId, copiedIngredients));
            CACHE.put(cacheKey, new CacheEntry(copiedIngredients, entry != null ? entry.packet() : null, now));
            trimCache();
        }

        return entry != null ? entry.packet() : null;
    }

    public static void handle(MachineRecipeStatePacket packet) {
        CacheKey cacheKey = new CacheKey(packet.containerId(), packet.profileId(), packet.requestKey());
        CacheEntry existing = CACHE.get(cacheKey);
        List<RequestedIngredient> requestedIngredients = existing != null ? existing.requestedIngredients() : List.of();
        long lastRequestMs = existing != null ? existing.lastRequestMs() : System.currentTimeMillis();
        CACHE.put(cacheKey, new CacheEntry(requestedIngredients, packet, lastRequestMs));
        trimCache();
    }

    private static List<RequestedIngredient> copyRequestedIngredients(List<RequestedIngredient> requestedIngredients) {
        return requestedIngredients.stream().map(RequestedIngredient::copy).toList();
    }

    private static void trimCache() {
        while (CACHE.size() > MAX_CACHE_ENTRIES) {
            CacheKey firstKey = CACHE.keySet().iterator().next();
            CACHE.remove(firstKey);
        }
    }

    private record CacheKey(int containerId, String profileId, String requestKey) {
    }

    private record CacheEntry(List<RequestedIngredient> requestedIngredients, @Nullable MachineRecipeStatePacket packet,
            long lastRequestMs) {
    }
}
