package com.lhy.ae2utility.client;

import com.lhy.ae2utility.client.cache.ClientCacheGeneration;
import com.lhy.ae2utility.jei.ClientRepoCraftableIndex;
import com.lhy.ae2utility.jei.CraftableStateCache;
import com.lhy.ae2utility.jei.JeiClientCacheContext;
import com.lhy.ae2utility.jei.MachineRecipeStateCache;

import net.minecraft.client.Minecraft;

/** Client-thread lifecycle coordinator. Owns no recipe data or persisted preferences.
 * Optional viewer state is reached only through a callback installed by its loaded adapter.
 */
public final class ClientCacheLifecycle {
    private static final ClientCacheGeneration SCOPE = new ClientCacheGeneration();
    private static Runnable optionalClear = () -> {};

    private ClientCacheLifecycle() {}

    public static void installOptionalClear(Runnable clear) { optionalClear = clear; }
    public static long generation() { return SCOPE.generation(); }

    /** Also called at request/reply boundaries: a menu can change between END ticks. */
    public static void synchronizeContext() {
        Minecraft mc = Minecraft.getInstance();
        if (SCOPE.observe(mc.level, mc.player, mc.player == null ? null : mc.player.containerMenu)) {
            clearData();
        }
    }

    public static void tick() {
        synchronizeContext();
        JeiClientCacheContext.advanceClientTick();
        ClientRepoCraftableIndex.advanceClientTick();
        CraftableStateCache.tick();
        MachineRecipeStateCache.tick();
    }

    public static void clear() {
        SCOPE.invalidate();
        clearData();
    }

    private static void clearData() {
        CraftableStateCache.clear();
        MachineRecipeStateCache.clear();
        ClientRepoCraftableIndex.invalidate();
        JeiClientCacheContext.clear();
        optionalClear.run();
    }
}
