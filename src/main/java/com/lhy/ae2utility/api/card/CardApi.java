package com.lhy.ae2utility.api.card;

import appeng.api.stacks.AEKey;
import net.minecraft.world.item.ItemStack;

/** Utilities for reading and updating AE2: Utility cards. */
public interface CardApi {
    CardApi INSTANCE = new CardApiImpl();

    boolean isNbtTearCard(ItemStack stack);

    NbtTearRule getNbtTearRule(ItemStack stack);

    void setNbtTearRule(ItemStack stack, NbtTearRule rule);

    boolean matchesNbtTear(AEKey expected, AEKey returned, NbtTearRule rule);

    boolean isRedstoneSignalCard(ItemStack stack);

    RedstoneSignalMode getRedstoneMode(ItemStack stack);

    void setRedstoneMode(ItemStack stack, RedstoneSignalMode mode);

    int getSignalDurationTicks(ItemStack stack);

    void setSignalDurationTicks(ItemStack stack, int ticks);

    void updateRedstoneSignal(RedstoneSignalHost host, boolean busy, boolean returnPending,
            boolean allowCraftOnFallingEdge);

    void onSuccessfulPatternPush(RedstoneSignalHost host, boolean busy, boolean returnPending);
}