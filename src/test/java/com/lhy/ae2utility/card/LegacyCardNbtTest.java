package com.lhy.ae2utility.card;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.lhy.ae2utility.item.NbtTearCardItem;
import com.lhy.ae2utility.testutil.MinecraftTestBootstrap;
import com.lhy.ae2utility.item.RedstoneSignalCardItem;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class LegacyCardNbtTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensure();
    }

    @Test
    void oldRedstoneKeysDefaultModesAndDurationSentinelAreStable() {
        // Static card NBT readers do not require registration of a mod item.
        ItemStack stack = new ItemStack(Items.PAPER);
        assertEquals("Ae2UtilityRedstoneMode", RedstoneSignalCardItem.MODE_TAG);
        assertEquals("Ae2UtilityRedstoneHoldTicks", RedstoneSignalCardItem.HOLD_TAG);
        assertEquals(RedstoneSignalCardMode.ORDER, RedstoneSignalCardItem.getMode(stack));
        assertEquals(20, RedstoneSignalCardItem.getHoldTicks(stack));
        stack.getOrCreateTag().putString("Ae2UtilityRedstoneMode", "unknown_old_value");
        assertEquals(RedstoneSignalCardMode.ORDER, RedstoneSignalCardItem.getMode(stack));
        for (var mode : RedstoneSignalCardMode.values()) {
            RedstoneSignalCardItem.setMode(stack, mode);
            assertEquals(mode, RedstoneSignalCardItem.getMode(stack));
        }
        stack.getOrCreateTag().putInt("Ae2UtilityRedstoneHoldTicks", 0);
        assertEquals(20, RedstoneSignalCardItem.getHoldTicks(stack));
        RedstoneSignalCardItem.setHoldTicks(stack, Integer.MIN_VALUE);
        assertEquals(2, RedstoneSignalCardItem.getHoldTicks(stack));
        RedstoneSignalCardItem.setHoldTicks(stack, Integer.MAX_VALUE);
        assertEquals(72000, RedstoneSignalCardItem.getHoldTicks(stack));
        assertEquals(6, RedstoneSignalCardItem.DEFAULT_PULSE_DURATION_TICKS);
        assertEquals(6, RedstoneSignalCardItem.resolveOutputDurationTicks(null));
    }

    @Test
    void oldTearWhitelistKeepsItsKeyAndIgnoresMalformedIds() {
        ItemStack stack = new ItemStack(Items.PAPER);
        assertEquals("Ae2UtilityNbtTearItems", NbtTearCardItem.ITEMS_TAG);
        assertTrue(NbtTearCardItem.getFilter(stack).itemIds().isEmpty());
        ListTag legacy = new ListTag();
        legacy.add(StringTag.valueOf("minecraft:stone"));
        legacy.add(StringTag.valueOf("not a valid id"));
        stack.getOrCreateTag().put("Ae2UtilityNbtTearItems", legacy);
        var stone = ResourceLocation.tryParse("minecraft:stone");
        assertEquals(List.of(stone), NbtTearCardItem.getFilter(stack).itemIds());
        NbtTearCardItem.setFilter(stack, new NbtTearFilter(List.of(stone)));
        assertEquals("minecraft:stone", stack.getOrCreateTag().getList("Ae2UtilityNbtTearItems", 8).getString(0));
        NbtTearCardItem.setFilter(stack, NbtTearFilter.DEFAULT);
        assertTrue(NbtTearCardItem.getFilter(stack).itemIds().isEmpty());
    }
}
