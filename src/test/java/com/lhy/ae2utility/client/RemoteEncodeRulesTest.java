package com.lhy.ae2utility.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.lhy.ae2utility.network.EncodePatternPacket;

class RemoteEncodeRulesTest {
    @AfterEach
    void reset() {
        RemoteEncodeRules.clearOnDisconnected();
    }

    @Test
    void doesNotApplyServerRulesBeforeSync() {
        List<EncodePatternPacket> packets = List.of(dummyPacket(), dummyPacket());
        assertTrue(!RemoteEncodeRules.hasSyncedRulesFromServer());
        assertEquals(Integer.MAX_VALUE, RemoteEncodeRules.remoteMaxJeBulkEncodePatternsPerSession());
        assertSame(packets, RemoteEncodeRules.capPacketsToServerBulkLimit(packets));
    }

    @Test
    void capsBulkEncodeAfterSync() {
        RemoteEncodeRules.receiveFromServer(true, true, 1);
        EncodePatternPacket first = dummyPacket();
        List<EncodePatternPacket> packets = List.of(first, dummyPacket());

        assertTrue(RemoteEncodeRules.remoteBlocksJeFullCategoryBatch());
        assertTrue(RemoteEncodeRules.remoteRequiresOpenEncodingMenuForJe());
        assertEquals(List.of(first), RemoteEncodeRules.capPacketsToServerBulkLimit(packets));
    }

    @Test
    void negativeOneDisablesRemoteCap() {
        RemoteEncodeRules.receiveFromServer(false, false, -1);
        assertEquals(Integer.MAX_VALUE, RemoteEncodeRules.remoteMaxJeBulkEncodePatternsPerSession());
    }

    private static EncodePatternPacket dummyPacket() {
        return new EncodePatternPacket(List.of(), List.of(), null, "", "", "", false, false, false, false, false, false, 0);
    }
}
