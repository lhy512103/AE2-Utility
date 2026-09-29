package com.lhy.ae2utility.init;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.lhy.ae2utility.Ae2UtilityMod;

public final class ModItems {
    public static final DeferredRegister<Item> REG = DeferredRegister.create(ForgeRegistries.ITEMS, Ae2UtilityMod.MOD_ID);
    private static final List<RegistryObject<? extends Item>> CREATIVE_TAB_ITEMS = new ArrayList<>();

    private ModItems() {
    }

    static <T extends Item> RegistryObject<T> register(String name, Supplier<T> factory) {
        RegistryObject<T> item = REG.register(name, factory);
        CREATIVE_TAB_ITEMS.add(item);
        return item;
    }

    public static List<RegistryObject<? extends Item>> creativeTabItems() {
        return CREATIVE_TAB_ITEMS;
    }
}
