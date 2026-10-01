package com.lhy.ae2utility.api.card;

import net.minecraft.world.item.ItemStack;

/** Public API for AE2 Utility cards (Forge 1.20.1). */
public interface CardApi {
    CardApi INSTANCE = new CardApiImpl();
    boolean isNbtTearCard(ItemStack stack);
    NbtTearRule getNbtTearRule(ItemStack stack);
    void setNbtTearRule(ItemStack stack, NbtTearRule rule);
    boolean isRedstoneSignalCard(ItemStack stack);
    RedstoneSignalMode getRedstoneMode(ItemStack stack);
    void setRedstoneMode(ItemStack stack, RedstoneSignalMode mode);
    int getSignalDurationTicks(ItemStack stack);
    void setSignalDurationTicks(ItemStack stack, int ticks);
    void updateRedstoneSignal(RedstoneSignalHost host, boolean busy, boolean returnPending, boolean allowCraftOnFallingEdge);
    void onSuccessfulPatternPush(RedstoneSignalHost host, boolean busy, boolean returnPending);
}
