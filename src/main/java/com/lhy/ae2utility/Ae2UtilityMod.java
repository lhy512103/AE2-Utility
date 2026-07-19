package com.lhy.ae2utility;

import com.lhy.ae2utility.compat.ModCapabilities;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import com.lhy.ae2utility.client.Ae2UtilityClientConfig;
import com.lhy.ae2utility.client.ModClientSetup;
import com.lhy.ae2utility.client.RemoteEncodeRules;
import com.lhy.ae2utility.command.Ae2UtilityCommands;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.lhy.ae2utility.integration.ae2.Ae2UtilitySlotSemantics;
import com.lhy.ae2utility.init.ModCommonSetup;
import com.lhy.ae2utility.init.ModDataComponents;
import com.lhy.ae2utility.init.ModItems;
import com.lhy.ae2utility.init.ModMenus;
import com.lhy.ae2utility.init.ModSetup;
import com.lhy.ae2utility.network.ModNetworking;

@Mod(Ae2UtilityMod.MOD_ID)
public class Ae2UtilityMod {
    public static final String MOD_ID = "ae2utility";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Ae2UtilityMod(IEventBus modBus, ModContainer modContainer) {
        Ae2UtilitySlotSemantics.bootstrap();
        ModItems.REG.register(modBus);
        ModDataComponents.REG.register(modBus);
        ModMenus.REG.register(modBus);
        modBus.addListener(ModNetworking::registerPayloads);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, Ae2UtilityCommands::register);
        Ae2UtilityServerGameplay.register(modBus);
        modBus.addListener(FMLCommonSetupEvent.class, ModCommonSetup::onCommonSetup);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, ModSetup::onCreativeTab);
        modContainer.registerConfig(ModConfig.Type.SERVER, Ae2UtilityServerConfig.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modContainer.registerConfig(ModConfig.Type.CLIENT, Ae2UtilityClientConfig.SPEC);
            modBus.addListener(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent.class, ModClientSetup::registerScreens);
            NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, Ae2UtilityMod::onClientLoggingOut);
            modBus.addListener(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent.class, Ae2UtilityMod::onClientSetup);
        }
    }

    private static void onClientLoggingOut(@SuppressWarnings("unused") ClientPlayerNetworkEvent.LoggingOut event) {
        RemoteEncodeRules.clearOnDisconnected();
    }

    private static void onClientSetup(@SuppressWarnings("unused") net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        // Guarded reference: EmiBatchScreenButtons touches EMI classes, so only load it when EMI is present.
        if (ModCapabilities.hasEmi()) {
            com.lhy.ae2utility.emi.EmiBatchScreenButtons.register(NeoForge.EVENT_BUS);
        }
    }
}
