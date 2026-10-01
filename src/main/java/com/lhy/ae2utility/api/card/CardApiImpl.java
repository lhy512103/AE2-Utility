package com.lhy.ae2utility.api.card;

import com.lhy.ae2utility.card.RedstoneSignalCardMode;
import com.lhy.ae2utility.card.NbtTearFilter;
import com.lhy.ae2utility.item.RedstoneSignalCardItem;
import com.lhy.ae2utility.item.NbtTearCardItem;
import java.util.Set;
import net.minecraft.world.item.ItemStack;

final class CardApiImpl implements CardApi {
    public boolean isNbtTearCard(ItemStack stack) { return stack != null && stack.getItem() instanceof NbtTearCardItem; }
    public NbtTearRule getNbtTearRule(ItemStack stack) { if (!isNbtTearCard(stack)) return NbtTearRule.ALL_ITEMS; return new NbtTearRule(Set.copyOf(NbtTearCardItem.getFilter(stack).itemIds())); }
    public void setNbtTearRule(ItemStack stack, NbtTearRule rule) { if (!isNbtTearCard(stack) || rule == null) throw new IllegalArgumentException("NBT tear card and rule are required"); NbtTearCardItem.setFilter(stack, new NbtTearFilter(java.util.List.copyOf(rule.itemIds()))); }
    public boolean isRedstoneSignalCard(ItemStack stack) { return stack != null && stack.getItem() instanceof RedstoneSignalCardItem; }
    public RedstoneSignalMode getRedstoneMode(ItemStack stack) { require(stack); return toPublic(RedstoneSignalCardItem.getMode(stack)); }
    public void setRedstoneMode(ItemStack stack, RedstoneSignalMode mode) { require(stack); if (mode == null) throw new IllegalArgumentException("mode is required"); RedstoneSignalCardItem.setMode(stack, toInternal(mode)); }
    public int getSignalDurationTicks(ItemStack stack) { require(stack); return RedstoneSignalCardItem.resolveOutputDurationTicks(stack); }
    public void setSignalDurationTicks(ItemStack stack, int ticks) { require(stack); RedstoneSignalCardItem.setHoldTicks(stack, ticks); }
    public void updateRedstoneSignal(RedstoneSignalHost host, boolean busy, boolean returnPending, boolean allowCraftOnFallingEdge) {
        if (host == null) throw new IllegalArgumentException("host is required");
        boolean active = busy || returnPending;
        if (active == host.lastActive()) return;
        host.setLastActive(active);
        RedstoneSignalMode mode = modeOf(host.signalCard());
        if (active) { if (mode == RedstoneSignalMode.UNTIL_RECIPE_COMPLETE) host.setContinuousSignal(true); else if (mode == RedstoneSignalMode.ORDER) host.triggerPulse(duration(host.signalCard())); }
        else { if (mode == RedstoneSignalMode.UNTIL_RECIPE_COMPLETE) host.setContinuousSignal(false); else if (allowCraftOnFallingEdge && mode == RedstoneSignalMode.CRAFT) host.triggerPulse(duration(host.signalCard())); }
    }
    public void onSuccessfulPatternPush(RedstoneSignalHost host, boolean busy, boolean returnPending) {
        if (host == null) throw new IllegalArgumentException("host is required");
        RedstoneSignalMode mode = modeOf(host.signalCard());
        if (mode == RedstoneSignalMode.UNTIL_RECIPE_COMPLETE) host.setContinuousSignal(true); else if (mode == RedstoneSignalMode.ORDER) host.triggerPulse(duration(host.signalCard()));
        host.setLastActive(mode == RedstoneSignalMode.UNTIL_RECIPE_COMPLETE || busy || returnPending);
    }
    private static RedstoneSignalMode modeOf(ItemStack s) { return s != null && s.getItem() instanceof RedstoneSignalCardItem ? toPublic(RedstoneSignalCardItem.getMode(s)) : null; }
    private static int duration(ItemStack s) { return s != null && s.getItem() instanceof RedstoneSignalCardItem ? RedstoneSignalCardItem.resolveOutputDurationTicks(s) : 1; }
    private static void require(ItemStack s) { if (!isCard(s)) throw new IllegalArgumentException("stack is not an AE2 Utility redstone signal card"); }
    private static boolean isCard(ItemStack s) { return s != null && s.getItem() instanceof RedstoneSignalCardItem; }
    private static RedstoneSignalMode toPublic(RedstoneSignalCardMode m) { return RedstoneSignalMode.valueOf(m.name()); }
    private static RedstoneSignalCardMode toInternal(RedstoneSignalMode m) { return RedstoneSignalCardMode.valueOf(m.name()); }
}
