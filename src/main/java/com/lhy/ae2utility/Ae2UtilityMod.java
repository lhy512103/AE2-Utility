package com.lhy.ae2utility;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import com.lhy.ae2utility.client.ModClientSetup;
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

    public Ae2UtilityMod(IEventBus modBus) {
        Ae2UtilitySlotSemantics.bootstrap();
        ModItems.REG.register(modBus);
        ModDataComponents.REG.register(modBus);
        ModMenus.REG.register(modBus);
        modBus.addListener(ModNetworking::registerPayloads);
        modBus.addListener(FMLCommonSetupEvent.class, ModCommonSetup::onCommonSetup);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, ModSetup::onCreativeTab);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent.class, ModClientSetup::registerScreens);
        }
    }
}
