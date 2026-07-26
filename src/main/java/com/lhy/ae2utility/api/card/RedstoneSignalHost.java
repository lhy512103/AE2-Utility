package com.lhy.ae2utility.api.card;

import net.minecraft.world.item.ItemStack;

/**
 * Adapter implemented by pattern-provider integrations that want the standard
 * AE2: Utility redstone-card state machine.
 */
public interface RedstoneSignalHost {
    ItemStack signalCard();

    boolean lastActive();

    void setLastActive(boolean active);

    void triggerPulse(int durationTicks);

    void setContinuousSignal(boolean active);
}