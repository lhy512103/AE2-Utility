package com.lhy.ae2utility.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

public class OptionalRuntimeBridgeTest {
    public static class RuntimeOwner {
        public static Object runtime;
        public Object instanceField;
    }

    public static class BrokenOwner {
        public static Object runtime = fail();
        private static Object fail() { throw new NoClassDefFoundError("optional dependency"); }
    }

    @Test
    void observesAvailabilityReplacementAndUnloadWithoutCachingRuntime() {
        var bridge = new OptionalRuntimeBridge<>(RuntimeOwner.class.getName(), "runtime", String.class);
        try {
            RuntimeOwner.runtime = null;
            assertNull(bridge.current());
            RuntimeOwner.runtime = "first";
            assertEquals("first", bridge.current());
            RuntimeOwner.runtime = "second";
            assertEquals("second", bridge.current());
            RuntimeOwner.runtime = null;
            assertNull(bridge.current());
            RuntimeOwner.runtime = 123;
            assertNull(bridge.current());
        } finally {
            RuntimeOwner.runtime = null;
        }
    }

    @Test
    void missingOptionalClassesFieldsAndNonStaticFieldsAreSafe() {
        assertNull(new OptionalRuntimeBridge<>("missing.optional.Plugin", "runtime", Object.class).current());
        assertNull(new OptionalRuntimeBridge<>(RuntimeOwner.class.getName(), "missing", Object.class).current());
        assertNull(new OptionalRuntimeBridge<>(RuntimeOwner.class.getName(), "instanceField", Object.class).current());
    }

    @Test
    void optionalDependencyLinkageFailuresDoNotEscape() {
        var bridge = new OptionalRuntimeBridge<>(BrokenOwner.class.getName(), "runtime", Object.class);
        assertNull(bridge.current());
        assertNull(bridge.current());
    }
}
