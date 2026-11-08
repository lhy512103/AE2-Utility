package com.lhy.ae2utility;

import com.lhy.ae2utility.compat.ModCapabilities;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;

import com.lhy.ae2utility.client.Ae2UtilityClientConfig;
import com.lhy.ae2utility.client.Ae2UtilityClientSetup;
import com.lhy.ae2utility.client.RemoteEncodeRules;
import com.lhy.ae2utility.command.Ae2UtilityCommands;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.RegisterCommandsEvent;

import com.lhy.ae2utility.integration.ae2.Ae2UtilitySlotSemantics;
import com.lhy.ae2utility.init.ModCommonSetup;
import com.lhy.ae2utility.init.ModItems;
import com.lhy.ae2utility.init.ModMenus;
import com.lhy.ae2utility.init.ModSetup;
import com.lhy.ae2utility.network.ModNetworking;

@Mod(Ae2UtilityMod.MOD_ID)
public class Ae2UtilityMod {
    public static final String MOD_ID = "ae2utility";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Ae2UtilityMod() {
        IEventBus modBus = net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus();
        var loadingContext = net.minecraftforge.fml.ModLoadingContext.get();
        ModContainer modContainer = loadingContext.getActiveContainer();
        Ae2UtilitySlotSemantics.bootstrap();
        ModItems.REG.register(modBus);
        ModMenus.REG.register(modBus);
        ModNetworking.register();
        MinecraftForge.EVENT_BUS.addListener(Ae2UtilityCommands::register);
        Ae2UtilityServerGameplay.register(modBus);
        modBus.addListener(ModCommonSetup::onCommonSetup);
        modBus.addListener(ModSetup::onCreativeTab);
        loadingContext.registerConfig(ModConfig.Type.SERVER, Ae2UtilityServerConfig.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            loadingContext.registerConfig(ModConfig.Type.CLIENT, Ae2UtilityClientConfig.SPEC);
            Ae2UtilityClientSetup.init(modContainer, modBus);
            MinecraftForge.EVENT_BUS.addListener(Ae2UtilityMod::onClientLoggingOut);
            modBus.addListener(Ae2UtilityMod::onClientSetup);
        }
    }

    private static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        RemoteEncodeRules.clearOnDisconnected();
    }

    private static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        // Guarded reference: EmiBatchScreenButtons touches EMI classes, so only load it when EMI is present.
        if (ModCapabilities.hasEmi()) {
            com.lhy.ae2utility.emi.EmiBatchScreenButtons.register(MinecraftForge.EVENT_BUS);
        }
    }
}
