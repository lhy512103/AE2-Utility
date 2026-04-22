package com.lhy.ae2utility;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import com.lhy.ae2utility.network.ModNetworking;

@Mod(Ae2UtilityMod.MOD_ID)
public class Ae2UtilityMod {
    public static final String MOD_ID = "ae2utility";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Ae2UtilityMod(IEventBus modBus) {
        modBus.addListener(ModNetworking::registerPayloads);
    }
}
