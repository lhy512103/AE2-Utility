package com.lhy.ae2utility.integration.ae2;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import net.minecraft.world.item.ItemStack;

class PatternProviderSignalDisconnectTest {
    @Test
    void disconnectClearsElectricalOutputButRetainsPendingAndEdgeBookkeeping() {
        Host host = new Host();
        host.pulse = 90;
        host.continuous = true;
        host.last = true;
        host.pending = true;
        host.ae2utility$onSignalHostDisconnected(null);
        assertEquals(0, host.pulse);
        assertFalse(host.continuous);
        assertTrue(host.last);
        assertTrue(host.pending);
        assertFalse(host.ae2utility$hasSignalPulse(0));
        host.ae2utility$onSignalHostDisconnected(null);
        assertEquals(0, host.pulse);
        assertTrue(host.last);
    }

    @Test
    void disconnectAlsoClearsPulseOnlyOutputWithoutCreatingAnOrder() {
        Host host = new Host();
        host.pulse = 200;
        host.last = true;
        host.ae2utility$onSignalHostDisconnected(null);
        assertEquals(0, host.pulse);
        assertTrue(host.last); // reconnecting same active job is not a fresh ORDER edge
        assertFalse(host.continuous);
    }

    private static final class Host implements PatternProviderSignalAccess {
        int pulse;
        boolean continuous;
        boolean last;
        boolean pending;
        public int ae2utility$getSignalPulseUntilTick() { return pulse; }
        public void ae2utility$setSignalPulseUntilTick(int value) { pulse = value; }
        public boolean ae2utility$isContinuousSignalActive() { return continuous; }
        public void ae2utility$setContinuousSignalActive(boolean value) { continuous = value; }
        public boolean ae2utility$getLastRedstoneActive() { return last; }
        public void ae2utility$setLastRedstoneActive(boolean value) { last = value; }
        public boolean ae2utility$isPendingOutputTracking() { return pending; }
        public ItemStack ae2utility$getRedstoneSignalCardStack() { return null; }
    }
}
