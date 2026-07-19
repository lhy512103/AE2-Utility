package com.lhy.ae2utility.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RemoteEncodeRulesTest {
    @AfterEach
    void reset() {
        RemoteEncodeRules.clearOnDisconnected();
    }

    @Test
    void doesNotApplyServerRulesBeforeSync() {
        List<?> packets = List.of("a", "b");
        assertTrue(!RemoteEncodeRules.hasSyncedRulesFromServer());
        assertEquals(Integer.MAX_VALUE, RemoteEncodeRules.remoteMaxJeBulkEncodePatternsPerSession());
        assertSame(packets, RemoteEncodeRules.capPacketsToServerBulkLimit((List) packets));
    }

    @Test
    void capsBulkEncodeAfterSync() {
        RemoteEncodeRules.receiveFromServer(true, true, 1);
        List<String> packets = List.of("first", "second");

        assertTrue(RemoteEncodeRules.remoteBlocksJeFullCategoryBatch());
        assertTrue(RemoteEncodeRules.remoteRequiresOpenEncodingMenuForJe());
        assertEquals(List.of("first"), RemoteEncodeRules.capPacketsToServerBulkLimit((List) packets));
    }

    @Test
    void negativeOneDisablesRemoteCap() {
        RemoteEncodeRules.receiveFromServer(false, false, -1);
        assertEquals(Integer.MAX_VALUE, RemoteEncodeRules.remoteMaxJeBulkEncodePatternsPerSession());
    }
}
