package com.lhy.ae2utility.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.Reference;
import java.util.Map;

import org.junit.jupiter.api.Test;

class WeakValueRegistryTest {
    @Test
    void findsLiveValuesThatReferenceTheirKeys() {
        Object layout = new Object();
        Controller controller = new Controller(layout);
        WeakValueRegistry<Object, Controller> registry = new WeakValueRegistry<>();
        registry.put(layout, controller);
        assertSame(controller, registry.get(layout));
        assertSame(controller, registry.find(value -> value.layout() == layout));
        assertNull(registry.find(value -> false));
    }

    @Test
    void doesNotKeepAControllerAliveThroughItsLayoutKey() throws ReflectiveOperationException {
        Object layout = new Object();
        Controller controller = new Controller(layout);
        WeakValueRegistry<Object, Controller> registry = new WeakValueRegistry<>();
        registry.put(layout, controller);

        // Inspect and clear the reference deterministically instead of relying on System.gc timing.
        var entriesField = WeakValueRegistry.class.getDeclaredField("entries");
        entriesField.setAccessible(true);
        Map<?, ?> entries = (Map<?, ?>) entriesField.get(registry);
        Object stored = entries.get(layout);
        assertTrue(stored instanceof Reference<?>, "Registry must not own its controller value");
        ((Reference<?>) stored).clear();

        assertNull(registry.get(layout));
        assertNull(registry.find(value -> true));
        assertEquals(0, entries.size());
    }

    @Test
    void replacementAndUnknownLayoutAreSafe() {
        Object layout = new Object();
        WeakValueRegistry<Object, Controller> registry = new WeakValueRegistry<>();
        Controller latest = new Controller(layout);
        registry.put(layout, new Controller(layout));
        registry.put(layout, latest);
        assertSame(latest, registry.get(layout));
        assertNull(registry.get(new Object()));
    }

    private record Controller(Object layout) {}
}
