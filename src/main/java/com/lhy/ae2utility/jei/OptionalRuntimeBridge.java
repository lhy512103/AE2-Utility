package com.lhy.ae2utility.jei;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Optional integration field accessor. Reads the current value on every call, including null on unload. */
public final class OptionalRuntimeBridge<T> {
    private final Class<T> runtimeType;
    private final Field field;

    public OptionalRuntimeBridge(String className, String fieldName, Class<T> runtimeType) {
        this.runtimeType = runtimeType;
        Field resolved = null;
        try {
            Field candidate = Class.forName(className, false, OptionalRuntimeBridge.class.getClassLoader())
                    .getField(fieldName);
            if (Modifier.isStatic(candidate.getModifiers())) resolved = candidate;
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            // Optional mod absent, incompatible, or inaccessible.
        }
        this.field = resolved;
    }

    public T current() {
        if (field == null) return null;
        try {
            Object value = field.get(null);
            return runtimeType.isInstance(value) ? runtimeType.cast(value) : null;
        } catch (IllegalAccessException | LinkageError | SecurityException ignored) {
            return null;
        }
    }
}
