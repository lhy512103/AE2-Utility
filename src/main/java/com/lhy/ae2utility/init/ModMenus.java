package com.lhy.ae2utility.init;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import com.lhy.ae2utility.Ae2UtilityMod;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> REG = DeferredRegister.create(ForgeRegistries.MENU_TYPES,
            Ae2UtilityMod.MOD_ID);

    private ModMenus() {
    }
}
