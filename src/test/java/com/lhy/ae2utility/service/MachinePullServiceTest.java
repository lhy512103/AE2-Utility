package com.lhy.ae2utility.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MachinePullServiceTest {
    @Test
    void clampsHugeCountsToTheStackLimit() {
        assertEquals(64, MachinePullService.clampRequestedCount(Integer.MAX_VALUE, 64));
        assertEquals(16, MachinePullService.clampRequestedCount(Integer.MAX_VALUE, 16));
        assertEquals(1, MachinePullService.clampRequestedCount(Integer.MAX_VALUE, 1));
    }

    @Test
    void preservesValidCounts() {
        assertEquals(3, MachinePullService.clampRequestedCount(3, 64));
        assertEquals(64, MachinePullService.clampRequestedCount(64, 64));
        assertEquals(1, MachinePullService.clampRequestedCount(1, 1));
    }

    @Test
    void invalidCountsRequireNoItems() {
        assertEquals(0, MachinePullService.clampRequestedCount(0, 64));
        assertEquals(0, MachinePullService.clampRequestedCount(-1, 64));
        assertEquals(0, MachinePullService.clampRequestedCount(Integer.MIN_VALUE, 64));
    }
}
