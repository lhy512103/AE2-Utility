package com.lhy.ae2utility.menu;

import net.minecraft.world.item.ItemStack;

import com.lhy.ae2utility.item.NbtTearCardItem;
import com.lhy.ae2utility.item.RedstoneSignalCardItem;

public final class PatternProviderFeatureCardPolicy {
    private PatternProviderFeatureCardPolicy() {
    }

    public static boolean accepts(ItemStack stack, boolean onlyAllowRedstoneCard) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (onlyAllowRedstoneCard) {
            return stack.getItem() instanceof RedstoneSignalCardItem;
        }
        return isFeatureCard(stack);
    }

    public static boolean isFeatureCard(ItemStack stack) {
        return stack != null && (stack.getItem() instanceof NbtTearCardItem
                || stack.getItem() instanceof RedstoneSignalCardItem);
    }

    public static boolean hasSameCardType(ItemStack candidate, ItemStack existing) {
        if (candidate == null || existing == null) {
            return false;
        }
        return (candidate.getItem() instanceof NbtTearCardItem
                && existing.getItem() instanceof NbtTearCardItem)
                || (candidate.getItem() instanceof RedstoneSignalCardItem
                        && existing.getItem() instanceof RedstoneSignalCardItem);
    }
}