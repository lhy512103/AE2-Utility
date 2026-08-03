package com.lhy.ae2utility.card;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NbtTearExecutionHelperTest {

    @AfterEach
    void clearContext() {
        NbtTearCardThreadLocal.clear();
    }

    @Test
    void rejectsReplacementPushWithoutActiveTearCardContext() {
        assertThrows(IllegalStateException.class,
                () -> NbtTearExecutionHelper.pushSparseInputsWithTear(null, null, List.of()));
    }

    @Test
    void tearCardContextIsExplicitlyScoped() {
        var filter = NbtTearFilter.DEFAULT;

        NbtTearCardThreadLocal.set(filter);
        assertSame(filter, NbtTearCardThreadLocal.get());

        NbtTearCardThreadLocal.clear();
        assertNull(NbtTearCardThreadLocal.get());
    }
}