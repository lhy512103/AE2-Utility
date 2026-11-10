package com.lhy.ae2utility.jei;

import java.util.List;
import org.jetbrains.annotations.Nullable;

import com.lhy.ae2utility.client.ClientCacheLifecycle;
import com.lhy.ae2utility.client.cache.ClientRequestCache;
import com.lhy.ae2utility.network.MachineRecipeStatePacket;
import com.lhy.ae2utility.network.ModNetworking;
import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;
import com.lhy.ae2utility.network.QueryMachineRecipeStatePacket;
import com.lhy.ae2utility.network.RecipeTransferPacketHelper;
import net.minecraft.client.Minecraft;

/** World/player/menu scoped; key = menu ID + profile + ingredient signature.
 * 64 access-order entries, 1s stock TTL, 500ms retry throttle. Nonces prevent replies
 * from an earlier menu/session (including a reused container ID) being accepted.
 */
public final class MachineRecipeStateCache {
    private static final ClientRequestCache<CacheKey, MachineRecipeStatePacket> CACHE =
            new ClientRequestCache<>(64, 1000, 500, ClientRequestCache::monotonicMillis);

    private static final java.util.Map<CacheKey, List<RequestedIngredient>> PENDING = new java.util.LinkedHashMap<>();
    private static long lastSendTime;

    private MachineRecipeStateCache() {}

    @Nullable
    public static MachineRecipeStatePacket getOrRequest(int containerId, String profileId,
            List<RequestedIngredient> requestedIngredients) {
        ClientCacheLifecycle.synchronizeContext();
        var player = Minecraft.getInstance().player;
        if (player == null || Minecraft.getInstance().level == null || player.containerMenu.containerId != containerId) return null;
        List<RequestedIngredient> copied = requestedIngredients.stream().map(RequestedIngredient::copy).toList();
        CacheKey key = new CacheKey(containerId, profileId, RecipeTransferPacketHelper.requestSignature(copied));
        MachineRecipeStatePacket packet = CACHE.get(key);
        if (CACHE.shouldRequest(key) && PENDING.size() < 64
                && RecipeTransferPacketHelper.canEncodeRequestedIngredients(copied)) PENDING.putIfAbsent(key, copied);
        return packet;
    }

    public static void handle(MachineRecipeStatePacket packet) {
        ClientCacheLifecycle.synchronizeContext();
        CACHE.complete(new CacheKey(packet.containerId(), packet.profileId(), packet.requestKey()), packet.requestNonce(), packet);
    }

    /** At most four distinct requests per tick/50ms, never from a render callback. */
    public static void tick() {
        long now = ClientRequestCache.monotonicMillis();
        if (Minecraft.getInstance().level == null || now - lastSendTime < 500) return;
        var iterator = PENDING.entrySet().iterator();
        int sent = 0;
        while (iterator.hasNext() && sent < 4) {
            var entry = iterator.next();
            iterator.remove();
            CacheKey key = entry.getKey();
            if (!CACHE.shouldRequest(key)) continue;
            long nonce = ClientRequestCache.nextNonce();
            CACHE.beginRequest(key, nonce);
            ModNetworking.sendToServer(new QueryMachineRecipeStatePacket(nonce, key.containerId(), key.profileId(), entry.getValue()));
            sent++;
        }
        if (sent > 0) lastSendTime = now;
    }

    public static void clear() { CACHE.clear(); PENDING.clear(); }
    private record CacheKey(int containerId, String profileId, String requestKey) {}
}
