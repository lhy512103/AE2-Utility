package com.lhy.ae2utility.client.jei;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EncodePatternViewModelTest {
    @Test
    void viewerAdapterDoesNotOwnPacketPreparationOrDispatch() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/com/lhy/ae2utility/jei/EncodePatternButtonController.class")) {
            assertNotNull(stream);
            String bytecode = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
            assertFalse(bytecode.contains("com/lhy/ae2utility/network/QueryEncodePreviewPacket"));
            assertFalse(bytecode.contains("com/lhy/ae2utility/jei/JeiEncodePacketFactory"));
            assertFalse(bytecode.contains("com/lhy/ae2utility/network/ModNetworking"));
            assertTrue(bytecode.contains("com/lhy/ae2utility/client/jei/EncodePatternQueryState"));
            assertTrue(bytecode.contains("com/lhy/ae2utility/client/jei/EncodePatternActions"));
        }
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void preparedContainersAreDetachedAndReadOnlyWithoutBootstrappingMinecraft() {
        Map source = new IdentityHashMap();
        source.put(null, true);
        EncodePatternViewModel snapshot = new EncodePatternViewModel(123, new ArrayList(), source, Map.of(), null);
        source.clear();
        assertEquals(123, snapshot.backdropColor());
        assertEquals(Boolean.TRUE, snapshot.craftableInputs().get(null));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.inputSlots().add(null));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.craftableInputs().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.chosenIngredients().put(null, null));
    }
}
