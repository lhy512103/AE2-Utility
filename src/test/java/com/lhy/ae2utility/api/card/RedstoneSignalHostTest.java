package com.lhy.ae2utility.api.card;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.lhy.ae2utility.api.Ae2UtilityApi;

import net.minecraft.world.item.ItemStack;

class RedstoneSignalHostTest {
    @Test
    void stateMachineTracksActivityWithoutEmittingWhenCardIsMissing() {
        TestHost host = new TestHost();

        Ae2UtilityApi.cards().updateRedstoneSignal(host, true, false, true);

        assertTrue(host.lastActive());
        assertEquals(0, host.pulseCount);
        assertEquals(0, host.continuousSignalChanges);

        Ae2UtilityApi.cards().updateRedstoneSignal(host, false, false, true);

        assertFalse(host.lastActive());
        assertEquals(0, host.pulseCount);
        assertEquals(0, host.continuousSignalChanges);
    }

    @Test
    void successfulPushDoesNotEmitWhenCardIsMissing() {
        TestHost host = new TestHost();

        Ae2UtilityApi.cards().onSuccessfulPatternPush(host, false, false);

        assertFalse(host.lastActive());
        assertEquals(0, host.pulseCount);
        assertEquals(0, host.continuousSignalChanges);
    }

    private static final class TestHost implements RedstoneSignalHost {
        private boolean lastActive;
        private int pulseCount;
        private int continuousSignalChanges;

        @Override
        public ItemStack signalCard() {
            return null;
        }

        @Override
        public boolean lastActive() {
            return lastActive;
        }

        @Override
        public void setLastActive(boolean active) {
            lastActive = active;
        }

        @Override
        public void triggerPulse(int durationTicks) {
            pulseCount++;
        }

        @Override
        public void setContinuousSignal(boolean active) {
            continuousSignalChanges++;
        }
    }
}