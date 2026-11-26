package com.lhy.ae2utility.integration.eaep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;

class EaepUploadMethodLookupTest {
    @Test
    void resolvesUploadMethodWithoutLoadingUnrelatedIntegrationTypes() throws Exception {
        Class<?> owner = loadWithoutOptionalIntegration();
        assertThrows(NoClassDefFoundError.class, () -> owner.getDeclaredMethod("searchKey", String.class));

        assertEquals("machine", EaepApiAccess.invokeStatic(owner, "searchKey", String.class,
                new Class<?>[] {String.class}, "machine"));
    }

    @Test
    void supportsPrivateLegacyMethodsAndPrimitiveResults() throws Exception {
        assertEquals(true, EaepApiAccess.invokeStatic(loadWithoutOptionalIntegration(), "containsPattern", boolean.class,
                new Class<?>[0]));
    }

    @Test
    void preservesReflectiveInvocationFailureContract() throws Exception {
        var failure = assertThrows(InvocationTargetException.class,
                () -> EaepApiAccess.invokeStatic(loadWithoutOptionalIntegration(), "fail", void.class,
                        new Class<?>[0]));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
    }

    private static Class<?> loadWithoutOptionalIntegration() throws ClassNotFoundException {
        String ownerName = LegacyUploadFixture.class.getName();
        String optionalName = OptionalIntegration.class.getName();
        ClassLoader loader = new ClassLoader(EaepUploadMethodLookupTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(optionalName)) {
                    throw new ClassNotFoundException(name);
                }
                if (!name.equals(ownerName)) {
                    return super.loadClass(name, resolve);
                }
                try (var stream = getResourceAsStream(name.replace('.', '/') + ".class")) {
                    if (stream == null) {
                        throw new ClassNotFoundException(name);
                    }
                    byte[] bytes = stream.readAllBytes();
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (IOException failure) {
                    throw new ClassNotFoundException(name, failure);
                }
            }
        };
        return loader.loadClass(ownerName);
    }

    public static class LegacyUploadFixture {
        public static String searchKey(String value) {
            return value;
        }

        private static boolean containsPattern() {
            return true;
        }

        public static void fail() {
            throw new IllegalStateException("upload failed");
        }

        private static void unrelatedMatrixMethod(OptionalIntegration unused) {
        }
    }

    public static class OptionalIntegration {
    }
}
