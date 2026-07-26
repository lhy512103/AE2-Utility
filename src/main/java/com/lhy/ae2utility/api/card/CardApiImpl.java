package com.lhy.ae2utility.api.card;

import java.util.List;
import java.util.Set;

import com.lhy.ae2utility.card.NbtTearFilter;
import com.lhy.ae2utility.card.RedstoneSignalCardMode;
import com.lhy.ae2utility.init.ModDataComponents;
import com.lhy.ae2utility.item.NbtTearCardItem;
import com.lhy.ae2utility.item.RedstoneSignalCardItem;

import appeng.api.stacks.AEKey;
import net.minecraft.world.item.ItemStack;

final class CardApiImpl implements CardApi {
    @Override
    public boolean isNbtTearCard(ItemStack stack) {
        return stack != null && stack.getItem() instanceof NbtTearCardItem;
    }

    @Override
    public NbtTearRule getNbtTearRule(ItemStack stack) {
        if (!isNbtTearCard(stack)) {
            return NbtTearRule.ALL_ITEMS;
        }
        NbtTearFilter filter = stack.getOrDefault(ModDataComponents.NBT_TEAR_FILTER, NbtTearFilter.DEFAULT);
        return new NbtTearRule(Set.copyOf(filter.itemIds()));
    }

    @Override
    public void setNbtTearRule(ItemStack stack, NbtTearRule rule) {
        requireNbtTearCard(stack);
        if (rule == null) {
            throw new IllegalArgumentException("rule is required");
        }
        stack.set(ModDataComponents.NBT_TEAR_FILTER, new NbtTearFilter(List.copyOf(rule.itemIds())));
    }

    @Override
    public boolean matchesNbtTear(AEKey expected, AEKey returned, NbtTearRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("rule is required");
        }
        return NbtTearFilter.matchesUnlockExpected(expected, returned,
                new NbtTearFilter(List.copyOf(rule.itemIds())));
    }

    @Override
    public boolean isRedstoneSignalCard(ItemStack stack) {
        return stack != null && stack.getItem() instanceof RedstoneSignalCardItem;
    }

    @Override
    public RedstoneSignalMode getRedstoneMode(ItemStack stack) {
        requireRedstoneCard(stack);
        return toPublic(stack.getOrDefault(ModDataComponents.REDSTONE_SIGNAL_CARD_MODE, RedstoneSignalCardMode.ORDER));
    }

    @Override
    public void setRedstoneMode(ItemStack stack, RedstoneSignalMode mode) {
        requireRedstoneCard(stack);
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }
        stack.set(ModDataComponents.REDSTONE_SIGNAL_CARD_MODE, toInternal(mode));
    }

    @Override
    public int getSignalDurationTicks(ItemStack stack) {
        requireRedstoneCard(stack);
        return RedstoneSignalCardItem.resolveOutputDurationTicks(stack);
    }

    @Override
    public void setSignalDurationTicks(ItemStack stack, int ticks) {
        requireRedstoneCard(stack);
        stack.set(ModDataComponents.REDSTONE_SIGNAL_HOLD_TICKS,
                Math.max(1, Math.min(ticks, RedstoneSignalCardItem.MAX_HOLD_TICKS)));
    }

    @Override
    public void updateRedstoneSignal(RedstoneSignalHost host, boolean busy, boolean returnPending,
            boolean allowCraftOnFallingEdge) {
        if (host == null) {
            throw new IllegalArgumentException("host is required");
        }
        boolean active = busy || returnPending;
        if (active == host.lastActive()) {
            return;
        }
        host.setLastActive(active);
        RedstoneSignalMode mode = modeOf(host.signalCard());
        if (active) {
            if (mode == RedstoneSignalMode.UNTIL_RECIPE_COMPLETE) {
                host.setContinuousSignal(true);
            } else if (mode == RedstoneSignalMode.ORDER) {
                host.triggerPulse(durationOrDefault(host.signalCard()));
            }
        } else if (mode == RedstoneSignalMode.UNTIL_RECIPE_COMPLETE) {
            host.setContinuousSignal(false);
        } else if (allowCraftOnFallingEdge && mode == RedstoneSignalMode.CRAFT) {
            host.triggerPulse(durationOrDefault(host.signalCard()));
        }
    }

    @Override
    public void onSuccessfulPatternPush(RedstoneSignalHost host, boolean busy, boolean returnPending) {
        if (host == null) {
            throw new IllegalArgumentException("host is required");
        }
        RedstoneSignalMode mode = modeOf(host.signalCard());
        if (mode == RedstoneSignalMode.UNTIL_RECIPE_COMPLETE) {
            host.setContinuousSignal(true);
        } else if (mode == RedstoneSignalMode.ORDER) {
            host.triggerPulse(durationOrDefault(host.signalCard()));
        }
        host.setLastActive(mode == RedstoneSignalMode.UNTIL_RECIPE_COMPLETE || busy || returnPending);
    }

    private static RedstoneSignalMode modeOf(ItemStack stack) {
        return stack != null && stack.getItem() instanceof RedstoneSignalCardItem
                ? toPublic(stack.getOrDefault(ModDataComponents.REDSTONE_SIGNAL_CARD_MODE, RedstoneSignalCardMode.ORDER))
                : null;
    }

    private static int durationOrDefault(ItemStack stack) {
        return stack != null && stack.getItem() instanceof RedstoneSignalCardItem
                ? RedstoneSignalCardItem.resolveOutputDurationTicks(stack)
                : 1;
    }

    private static void requireNbtTearCard(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof NbtTearCardItem)) {
            throw new IllegalArgumentException("stack is not an AE2: Utility NBT tear card");
        }
    }

    private static void requireRedstoneCard(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof RedstoneSignalCardItem)) {
            throw new IllegalArgumentException("stack is not an AE2: Utility redstone signal card");
        }
    }

    private static RedstoneSignalMode toPublic(RedstoneSignalCardMode mode) {
        return switch (mode) {
            case ORDER -> RedstoneSignalMode.ORDER;
            case CRAFT -> RedstoneSignalMode.CRAFT;
            case UNTIL_RECIPE_COMPLETE -> RedstoneSignalMode.UNTIL_RECIPE_COMPLETE;
        };
    }

    private static RedstoneSignalCardMode toInternal(RedstoneSignalMode mode) {
        return switch (mode) {
            case ORDER -> RedstoneSignalCardMode.ORDER;
            case CRAFT -> RedstoneSignalCardMode.CRAFT;
            case UNTIL_RECIPE_COMPLETE -> RedstoneSignalCardMode.UNTIL_RECIPE_COMPLETE;
        };
    }
}