package com.lhy.ae2utility.init;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.item.NbtTearCardItem;
import com.lhy.ae2utility.item.RedstoneSignalCardItem;
import com.lhy.ae2utility.item.RecipeFinderItem;

public final class ModItems {
    public static final DeferredRegister.Items REG = DeferredRegister.createItems(Ae2UtilityMod.MOD_ID);

    public static final DeferredItem<NbtTearCardItem> NBT_TEAR_CARD = REG.registerItem("nbt_tear_card",
            props -> new NbtTearCardItem(props.stacksTo(64)));
    public static final DeferredItem<RedstoneSignalCardItem> REDSTONE_SIGNAL_CARD = REG.registerItem("redstone_signal_card",
            props -> new RedstoneSignalCardItem(props.stacksTo(64)));
    public static final DeferredItem<RecipeFinderItem> RECIPE_FINDER = REG.registerItem("recipe_finder",
            props -> new RecipeFinderItem(props.stacksTo(1)));

    private ModItems() {
    }
}
