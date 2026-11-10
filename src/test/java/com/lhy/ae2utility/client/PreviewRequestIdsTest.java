package com.lhy.ae2utility.client;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PreviewRequestIdsTest {
    @Test
    void interleavedJeiAndEmiRequestsNeverShareAnId() {
        Set<Integer> ids = new HashSet<>();
        for (int layout = 0; layout < 1000; layout++) {
            int jei = PreviewRequestIds.next();
            int emi = PreviewRequestIds.next();
            int refreshedJei = PreviewRequestIds.next();
            assertNotEquals(0, jei);
            assertNotEquals(0, emi);
            assertNotEquals(0, refreshedJei);
            assertTrue(ids.add(jei));
            assertTrue(ids.add(emi));
            assertTrue(ids.add(refreshedJei));
        }
    }

    @Test
    void multicastResponseCannotMatchTheOtherViewerOrAnInvalidatedJeiRequest() {
        int oldJei = PreviewRequestIds.next();
        int emi = PreviewRequestIds.next();
        int currentJei = PreviewRequestIds.next();
        int response = currentJei;
        assertEquals(currentJei, response);
        assertNotEquals(oldJei, response);
        assertNotEquals(emi, response);
    }

    @Test
    void bothProductionConsumersUseTheSharedAllocatorWithoutInitializingMinecraft() throws Exception {
        for (String owner : new String[] { "client/jei/EncodePatternQueryState", "emi/EmiEncodePatternButtonWidget" }) {
            try (var stream = getClass().getResourceAsStream("/com/lhy/ae2utility/" + owner + ".class")) {
                assertNotNull(stream, owner);
                String bytecode = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertTrue(bytecode.contains("com/lhy/ae2utility/client/PreviewRequestIds"));
                assertFalse(bytecode.contains("NEXT_PREVIEW_REQUEST_ID"));
            }
        }
    }
}
