package com.lhy.ae2utility.network;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NetworkValidationTest {
    @Test
    void acceptsBoundarySizes() {
        assertDoesNotThrow(() -> NetworkValidation.requireSize(0, 10, "items"));
        assertDoesNotThrow(() -> NetworkValidation.requireSize(10, 10, "items"));
    }

    @Test
    void rejectsNegativeAndOversizedLists() {
        assertThrows(IllegalArgumentException.class, () -> NetworkValidation.requireSize(-1, 10, "items"));
        assertThrows(IllegalArgumentException.class, () -> NetworkValidation.requireSize(11, 10, "items"));
    }
}
