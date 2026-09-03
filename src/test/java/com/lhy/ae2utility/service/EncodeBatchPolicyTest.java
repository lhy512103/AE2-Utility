package com.lhy.ae2utility.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.lhy.ae2utility.network.EncodePatternPacket;

class EncodeBatchPolicyTest {
    @Test
    void leavesShortBatchesUntouched() {
        List<EncodePatternPacket> list = List.of();
        assertSame(list, EncodeBatchPolicy.cap(list, 10));
    }

    @Test
    void capsLongBatches() {
        EncodePatternPacket a = dummyPacket();
        EncodePatternPacket b = dummyPacket();
        List<EncodePatternPacket> values = List.of(a, b, dummyPacket());
        assertEquals(List.of(a, b), EncodeBatchPolicy.cap(values, 2));
    }

    private static EncodePatternPacket dummyPacket() {
        return new EncodePatternPacket(List.of(), List.of(), null, "", "", "", false, false, false, false, false, false, 0);
    }
}
