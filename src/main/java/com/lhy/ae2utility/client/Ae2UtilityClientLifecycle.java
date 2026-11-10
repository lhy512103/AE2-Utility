package com.lhy.ae2utility.client;

import com.lhy.ae2utility.compat.ModCapabilities;

import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client event signatures and optional adapter linkage stay off the common entry point. */
public final class Ae2UtilityClientLifecycle {
    private Ae2UtilityClientLifecycle() {
    }

    public static void register(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener(Ae2UtilityClientLifecycle::onLoggingOut);
        modBus.addListener(Ae2UtilityClientLifecycle::onClientSetup);
        modBus.addListener(Ae2UtilityClientLifecycle::onRegisterReloadListeners);
        if (ModCapabilities.isLoaded("jei")) {
            com.lhy.ae2utility.jei.JeiClientCacheLifecycle.install();
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        RemoteEncodeRules.clearOnDisconnected();
        ClientCacheLifecycle.clear();
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources ->
                Minecraft.getInstance().execute(ClientCacheLifecycle::clear));
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        if (ModCapabilities.hasEmi()) {
            com.lhy.ae2utility.emi.EmiBatchScreenButtons.register(MinecraftForge.EVENT_BUS);
        }
    }
}
