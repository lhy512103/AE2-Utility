package com.lhy.ae2utility.client;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.network.EncodePatternPacket;

import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

class UploadQueueDisconnectTest {
    @AfterEach
    void reset() {
        SequentialUploadQueue.cancelAllQuiet();
        InventoryPatternUploadQueue.clearOnDisconnected();
        RemoteEncodeRules.clearOnDisconnected();
    }

    @Test
    void logoutClearsQueuesAcknowledgementsAndProviderSelection() throws Exception {
        Class<?> sequential = SequentialUploadQueue.class;
        EncodePatternPacket packet = new EncodePatternPacket(List.of(), List.of(), null,
                "test", "", "", false, false, false, false, true, false, 1);
        collection(sequential, "PENDING").add(packet);
        collection(sequential, "IN_FLIGHT").add(packet);
        collection(sequential, "SUCCEEDED").add("done");
        collection(sequential, "FAILED").add("failed");
        for (String name : List.of("missingBlankFailCount", "expectedResults", "receivedResults", "waitTicks")) {
            set(sequential, name, 5);
        }
        for (String name : List.of("waitingForProviderScreen", "frozenUntilServerEncodeResponse", "awaitingProviderUploadCompletion")) {
            set(sequential, name, true);
        }

        Class<?> inventory = InventoryPatternUploadQueue.class;
        collection(inventory, "PENDING_SLOTS").add(4);
        Class<?> group = Class.forName(inventory.getName() + "$PendingGroup");
        var constructor = group.getDeclaredConstructor(List.class, String.class);
        constructor.setAccessible(true);
        collection(inventory, "PENDING_GROUPS").add(constructor.newInstance(List.of(4), "machine"));
        for (String name : List.of("awaitingUploadAckSlot", "awaitingProviderPreparationSlot", "preparedNetworkUploadSlot",
                "totalPatterns", "sentPatterns", "waitTicks")) {
            set(inventory, name, 4);
        }
        for (String name : List.of("selectingProvider", "manualProviderChoiceRequired", "awaitingProviderListOpen")) {
            set(inventory, name, true);
        }
        set(inventory, "providerId", 42L);
        for (String name : List.of("providerName", "currentSearchKey", "currentPatternName")) {
            set(inventory, name, "old server");
        }
        set(SequentialUploadProgressState.class, "currentPatternName", "old pattern");
        set(EaepPendingProviderSearch.class, "hasPending", true);
        set(EaepPendingProviderSearch.class, "pendingResolvedFilter", "old filter");
        EaepRememberedProviderChoice.record("machine", "old provider");
        RemoteEncodeRules.receiveFromServer(true, true, 1);

        var logout = Ae2UtilityMod.class.getDeclaredMethod("onClientLoggingOut", ClientPlayerNetworkEvent.LoggingOut.class);
        logout.setAccessible(true);
        logout.invoke(null, new Object[] {null});

        for (String name : List.of("PENDING", "IN_FLIGHT", "SUCCEEDED", "FAILED")) {
            assertTrue(collection(sequential, name).isEmpty(), name);
        }
        for (String name : List.of("missingBlankFailCount", "expectedResults", "receivedResults", "waitTicks")) {
            assertEquals(0, get(sequential, name), name);
        }
        for (String name : List.of("waitingForProviderScreen", "frozenUntilServerEncodeResponse", "awaitingProviderUploadCompletion")) {
            assertEquals(false, get(sequential, name), name);
        }
        assertTrue(collection(inventory, "PENDING_SLOTS").isEmpty());
        assertTrue(collection(inventory, "PENDING_GROUPS").isEmpty());
        for (String name : List.of("awaitingUploadAckSlot", "awaitingProviderPreparationSlot", "preparedNetworkUploadSlot")) {
            assertNull(get(inventory, name), name);
        }
        for (String name : List.of("totalPatterns", "sentPatterns", "waitTicks")) {
            assertEquals(0, get(inventory, name), name);
        }
        for (String name : List.of("selectingProvider", "manualProviderChoiceRequired", "awaitingProviderListOpen")) {
            assertEquals(false, get(inventory, name), name);
        }
        assertEquals(0L, get(inventory, "providerId"));
        for (String name : List.of("providerName", "currentSearchKey", "currentPatternName")) {
            assertEquals("", get(inventory, name), name);
        }
        assertNull(SequentialUploadProgressState.currentPatternName());
        assertEquals(false, get(EaepPendingProviderSearch.class, "hasPending"));
        assertEquals("", get(EaepPendingProviderSearch.class, "pendingResolvedFilter"));
        assertNull(EaepRememberedProviderChoice.lookup("machine"));
        assertFalse(RemoteEncodeRules.hasSyncedRulesFromServer());
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void set(Class<?> type, String name, Object value) throws Exception {
        field(type, name).set(null, value);
    }

    private static Object get(Class<?> type, String name) throws Exception {
        return field(type, name).get(null);
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> collection(Class<?> type, String name) throws Exception {
        return (Collection<Object>) get(type, name);
    }
}
