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

    /**
     * Reconciles a level output; implementations must be idempotent. Called even
     * without an activity edge so card removal, configuration and reload settle.
     * All host callbacks run synchronously on the caller's thread; invoke the
     * card state machine on the logical server thread that owns the host.
     */
    void setContinuousSignal(boolean active);
}
