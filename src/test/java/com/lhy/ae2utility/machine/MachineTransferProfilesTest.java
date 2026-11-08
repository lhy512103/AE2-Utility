package com.lhy.ae2utility.machine;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;

class MachineTransferProfilesTest {
    private static final String PROFILE = "com.lhy.ae2utility.machine.MachineTransferProfile";
    private static final String PROFILES = "com.lhy.ae2utility.machine.MachineTransferProfiles";

    @Test
    void commonProfilesAndServerServicesHaveNoViewerOrClientBytecodeReferences() throws IOException {
        for (String name : List.of(PROFILE, PROFILES,
                "com.lhy.ae2utility.service.MachinePullService",
                "com.lhy.ae2utility.service.MachineRecipeStateService")) {
            String bytecode = new String(classBytes(name), StandardCharsets.ISO_8859_1);
            for (String forbidden : List.of("mezz/jei/", "com/lhy/ae2utility/jei/", "net/minecraft/client/")) {
                assertFalse(bytecode.contains(forbidden), name + " references " + forbidden);
            }
        }
    }

    @Test
    void serverLookupsInitializeWithoutJeiAndPreserveBuiltInProfiles() throws Exception {
        ClassLoader loader = withoutRecipeViewers();
        Class<?> profiles = Class.forName(PROFILES, true, loader);
        Class<?> profile = loader.loadClass(PROFILE);
        List<?> all = (List<?>) profiles.getMethod("all").invoke(null);
        assertEquals(List.of("furnace", "smoker", "blast_furnace", "stonecutter", "smithing", "inscriber"),
                all.stream().map(value -> id(profile, value)).toList());
        int[][] slots = {{0}, {0}, {0}, {0}, {0, 1, 2}, {40, 42, 41}};
        for (int i = 0; i < all.size(); i++) {
            Object value = all.get(i);
            assertSame(value, profiles.getMethod("byId", String.class).invoke(null, id(profile, value)));
            assertArrayEquals(slots[i], (int[]) profile.getMethod("inputSlotIndices").invoke(value));
        }
        assertNull(profiles.getMethod("byId", String.class).invoke(null, "missing"));
        assertNull(profiles.getMethod("forMenu", AbstractContainerMenu.class).invoke(null, new Object[]{null}));
        assertEquals("furnace", profiles.getMethod("resolveId", ResourceLocation.class).invoke(null,
                ResourceLocation.fromNamespaceAndPath("ae2utility", "furnace")));
        assertNull(profiles.getMethod("resolveId", ResourceLocation.class).invoke(null,
                ResourceLocation.fromNamespaceAndPath("another_mod", "furnace")));
        assertThrows(UnsupportedOperationException.class, all::clear);
    }

    @Test
    void serverRegistrationAndSlotCopiesWorkWithoutJei() throws Exception {
        ClassLoader loader = withoutRecipeViewers();
        Class<?> profiles = Class.forName(PROFILES, true, loader);
        Class<?> profile = loader.loadClass(PROFILE);
        int[] slots = {2, 4};
        Object value = profile.getConstructor(String.class, Class.class, int[].class)
                .newInstance("test:machine", Object.class, slots);
        slots[0] = 99;
        profiles.getMethod("register", profile).invoke(null, value);
        assertSame(value, profiles.getMethod("byId", String.class).invoke(null, "test:machine"));
        assertEquals("test:machine", profiles.getMethod("resolveId", ResourceLocation.class).invoke(null,
                ResourceLocation.fromNamespaceAndPath("test", "machine")));
        int[] copy = (int[]) profile.getMethod("inputSlotIndices").invoke(value);
        assertArrayEquals(new int[]{2, 4}, copy);
        copy[0] = 88;
        assertEquals(2, profile.getMethod("inputSlotIndex", int.class).invoke(value, 0));
        InvocationTargetException duplicate = assertThrows(InvocationTargetException.class,
                () -> profiles.getMethod("register", profile).invoke(null, value));
        assertInstanceOf(IllegalArgumentException.class, duplicate.getCause());
        InvocationTargetException missing = assertThrows(InvocationTargetException.class,
                () -> profiles.getMethod("register", profile).invoke(null, new Object[]{null}));
        assertInstanceOf(IllegalArgumentException.class, missing.getCause());
    }

    private static String id(Class<?> profile, Object value) {
        try {
            return (String) profile.getMethod("id").invoke(value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] classBytes(String name) throws IOException {
        try (InputStream stream = MachineTransferProfilesTest.class.getClassLoader()
                .getResourceAsStream(name.replace('.', '/') + ".class")) {
            assertNotNull(stream, name);
            return stream.readAllBytes();
        }
    }

    private static ClassLoader withoutRecipeViewers() {
        return new ClassLoader(MachineTransferProfilesTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    // Also simulate optional machines being absent, independent of the dev classpath.
                    if (name.startsWith("mezz.jei.") || name.startsWith("dev.emi.")
                            || name.startsWith("com.lhy.ae2utility.jei.")
                            || name.startsWith("net.minecraft.client.")
                            || name.startsWith("net.pedroksl.advanced_ae.")
                            || name.startsWith("com.glodblock.github.extendedae.")) {
                        throw new ClassNotFoundException("Not installed on isolated server: " + name);
                    }
                    if (name.equals(PROFILE) || name.equals(PROFILES)) {
                        Class<?> loaded = findLoadedClass(name);
                        if (loaded == null) {
                            try {
                                byte[] bytes = classBytes(name);
                                loaded = defineClass(name, bytes, 0, bytes.length);
                            } catch (IOException e) {
                                throw new ClassNotFoundException(name, e);
                            }
                        }
                        if (resolve) resolveClass(loaded);
                        return loaded;
                    }
                    return super.loadClass(name, resolve);
                }
            }
        };
    }
}
