package com.lhy.ae2utility.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.menu.NbtTearCardMenu;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> REG = DeferredRegister.create(Registries.MENU, Ae2UtilityMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<NbtTearCardMenu>> NBT_TEAR_CARD = REG.register("nbt_tear_card",
            () -> IMenuTypeExtension.create(NbtTearCardMenu::fromNetwork));

    private ModMenus() {
    }
}
