package com.lhy.ae2utility.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Item registry ids via {@link ForgeRegistries#ITEMS}. */
public final class ItemIds {
    private static final ResourceLocation AIR = ResourceLocation.parse("minecraft:air");

    private ItemIds() {
    }

    public static ResourceLocation of(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id != null ? id : AIR;
    }

    public static ResourceLocation of(ItemStack stack) {
        return of(stack.getItem());
    }
    public static int stackHash(net.minecraft.world.item.ItemStack stack) {
        return java.util.Objects.hash(stack.getItem(), stack.getTag());
    }
}
