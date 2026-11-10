package com.lhy.ae2utility.api.card;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import net.minecraft.world.item.ItemStack;

class RedstoneSignalStateTest {
    @Test
    void orderPulsesOnRisingEdgeNotSteadyLevelOrFallingEdge() {
        Host host = new Host();
        update(host, true, RedstoneSignalMode.ORDER, true);
        update(host, true, RedstoneSignalMode.ORDER, true);
        update(host, false, RedstoneSignalMode.ORDER, true);
        assertEquals(List.of(37), host.pulses);
        assertFalse(host.continuous);
    }

    @Test
    void craftOnlyPulsesOnAllowedFallingEdge() {
        Host host = new Host();
        update(host, true, RedstoneSignalMode.CRAFT, true);
        update(host, false, RedstoneSignalMode.CRAFT, false);
        assertTrue(host.pulses.isEmpty());
        update(host, true, RedstoneSignalMode.CRAFT, true);
        update(host, false, RedstoneSignalMode.CRAFT, true);
        update(host, false, RedstoneSignalMode.CRAFT, true);
        assertEquals(List.of(37), host.pulses);
    }

    @Test
    void untilIsLevelDrivenAcrossModeChangesRemovalAndReload() {
        Host host = new Host();
        update(host, true, RedstoneSignalMode.ORDER, true);
        update(host, true, RedstoneSignalMode.UNTIL_RECIPE_COMPLETE, true);
        assertTrue(host.continuous);
        update(host, true, RedstoneSignalMode.CRAFT, true);
        assertFalse(host.continuous);
        update(host, true, RedstoneSignalMode.UNTIL_RECIPE_COMPLETE, true);
        update(host, true, null, true); // unplug card without an activity edge
        assertFalse(host.continuous);
        host.continuous = true; // old NBT can restore a stale level with lastActive=false
        host.last = false;
        update(host, false, RedstoneSignalMode.UNTIL_RECIPE_COMPLETE, true);
        assertFalse(host.continuous);
        assertEquals(List.of(37), host.pulses); // configuration must not fabricate orders
    }

    @Test
    void successfulPushIsAnOrderEventEvenIfProviderNeverBecomesBusy() {
        Host host = new Host();
        CardApiImpl.successfulPush(host, false, RedstoneSignalMode.ORDER, 6);
        CardApiImpl.successfulPush(host, false, RedstoneSignalMode.ORDER, 6);
        assertEquals(List.of(6, 6), host.pulses);
        assertFalse(host.last);
        CardApiImpl.successfulPush(host, false, RedstoneSignalMode.UNTIL_RECIPE_COMPLETE, 6);
        assertTrue(host.continuous);
        assertTrue(host.last);
        update(host, true, RedstoneSignalMode.UNTIL_RECIPE_COMPLETE, false);
        assertTrue(host.continuous);
        update(host, false, RedstoneSignalMode.UNTIL_RECIPE_COMPLETE, false);
        assertFalse(host.continuous);
        CardApiImpl.successfulPush(host, true, RedstoneSignalMode.CRAFT, 6);
        assertEquals(List.of(6, 6), host.pulses);
    }

    @Test
    void returnPendingKeepsHostActiveAndMissingCardDoesNotPulse() {
        Host host = new Host();
        host.last = true;
        host.continuous = true;
        new CardApiImpl().updateRedstoneSignal(host, false, true, true);
        assertTrue(host.last);
        assertFalse(host.continuous);
        assertTrue(host.pulses.isEmpty());
        new CardApiImpl().updateRedstoneSignal(host, false, false, true);
        assertFalse(host.last);
    }

    private static void update(Host host, boolean active, RedstoneSignalMode mode, boolean allowFall) {
        CardApiImpl.updateSignalState(host, active, allowFall, mode, 37);
    }

    private static final class Host implements RedstoneSignalHost {
        boolean last;
        boolean continuous;
        final List<Integer> pulses = new ArrayList<>();
        public ItemStack signalCard() { return null; }
        public boolean lastActive() { return last; }
        public void setLastActive(boolean active) { last = active; }
        public void triggerPulse(int durationTicks) { pulses.add(durationTicks); }
        public void setContinuousSignal(boolean active) { continuous = active; }
    }
}
