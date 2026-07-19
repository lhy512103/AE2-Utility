package com.lhy.ae2utility.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

class EncodeBatchPolicyTest {
    @Test
    void leavesShortBatchesUntouched() {
        List list = List.of();
        assertSame(list, EncodeBatchPolicy.cap(list, 10));
    }

    @Test
    void capsLongBatches() {
        List<String> values = List.of("a", "b", "c");
        assertEquals(List.of("a", "b"), EncodeBatchPolicy.cap((List) values, 2));
    }
}
