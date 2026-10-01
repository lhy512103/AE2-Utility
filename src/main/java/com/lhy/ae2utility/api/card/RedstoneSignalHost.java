package com.lhy.ae2utility.api.card;

import net.minecraft.world.item.ItemStack;

/** Adapter implemented by AE2 pattern-provider mixins. */
public interface RedstoneSignalHost {
    ItemStack signalCard();
    boolean lastActive();
    void setLastActive(boolean active);
    void triggerPulse(int durationTicks);
    void setContinuousSignal(boolean active);
}
