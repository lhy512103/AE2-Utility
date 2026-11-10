package com.lhy.ae2utility.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;

class CuriosWirelessTerminalLookupIsolationTest {
    private static final String FACADE = "com.lhy.ae2utility.service.CuriosWirelessTerminalLookup";

    @Test
    void commonFacadeAndSignaturesLoadWithNoOptionalApisOrClientClasses() throws Exception {
        List<String> attempted = new ArrayList<>();
        Class<?> facade = Class.forName(FACADE, true, isolatedFacade(attempted));
        facade.getDeclaredMethods();
        facade.getDeclaredFields();
        assertEquals(List.of(), facade.getMethod("findCandidates", Player.class).invoke(null, new Object[]{null}));
        assertEquals(false, facade.getMethod("hasCandidate", Player.class).invoke(null, new Object[]{null}));
        assertNull(facade.getMethod("items", Player.class).invoke(null, new Object[]{null}));
        assertTrue(attempted.isEmpty(), attempted.toString());
    }

    @Test
    void curiosOnlyOrMissingCuriosNeverResolvesEitherWirelessLocatorAdapter() throws Exception {
        List<String> attempted = new ArrayList<>();
        Class<?> facade = Class.forName(FACADE, true, isolatedFacade(attempted));
        Method locator = facade.getDeclaredMethod("locator", String.class, int.class, boolean.class, Predicate.class);
        locator.setAccessible(true);
        for (Set<String> mods : List.of(Set.<String>of(), Set.of("curios"), Set.of("ae2wtlib"),
                Set.of("wcwt"), Set.of("ae2wtlib", "wcwt"))) {
            for (boolean wcwtStack : List.of(false, true)) {
                assertNull(locator.invoke(null, "terminal", 0, wcwtStack, (Predicate<String>) mods::contains),
                        mods + " wcwtStack=" + wcwtStack);
            }
        }
        assertTrue(attempted.isEmpty(), "Absent optional locator was resolved: " + attempted);
    }

    @Test
    void metadataSelectsOnlyInstalledProvidersAndKeepsWcwtPrecedence() throws Exception {
        List<String> attempted = new ArrayList<>();
        Class<?> facade = Class.forName(FACADE, true, isolatedFacade(attempted));
        Method provider = facade.getDeclaredMethod("locatorProvider", boolean.class, Predicate.class);
        provider.setAccessible(true);
        assertProvider(provider, Set.of("curios"), false, "NONE");
        assertProvider(provider, Set.of("curios"), true, "NONE");
        assertProvider(provider, Set.of("curios", "ae2wtlib"), false, "AE2WTLIB");
        assertProvider(provider, Set.of("curios", "ae2wtlib"), true, "AE2WTLIB");
        assertProvider(provider, Set.of("curios", "wcwt"), false, "NONE");
        assertProvider(provider, Set.of("curios", "wcwt"), true, "WCWT");
        assertProvider(provider, Set.of("curios", "wireless_comprehensive_work_terminal"), true, "WCWT");
        assertProvider(provider, Set.of("curios", "ae2wtlib", "wcwt"), false, "AE2WTLIB");
        assertProvider(provider, Set.of("curios", "ae2wtlib", "wcwt"), true, "WCWT");
        assertTrue(attempted.isEmpty(), "Metadata selection loaded an optional target: " + attempted);
    }

    @Test
    void commonFacadeBytecodeHasNoDirectOptionalTypeReferences() throws IOException {
        String bytecode = new String(classBytes(FACADE), StandardCharsets.ISO_8859_1);
        for (String prefix : List.of("top/theillusivec4/curios/", "de/mari_023/ae2wtlib/", "com/lhy/wcwt/",
                "net/minecraft/client/")) {
            assertFalse(bytecode.contains(prefix), "Common facade links " + prefix);
        }
    }

    private static void assertProvider(Method method, Set<String> mods, boolean wcwtStack, String expected)
            throws ReflectiveOperationException {
        assertEquals(expected, method.invoke(null, wcwtStack, (Predicate<String>) mods::contains).toString(),
                mods + " wcwtStack=" + wcwtStack);
    }

    private static byte[] classBytes(String name) throws IOException {
        try (InputStream stream = CuriosWirelessTerminalLookupIsolationTest.class.getClassLoader()
                .getResourceAsStream(name.replace('.', '/') + ".class")) {
            assertNotNull(stream, name);
            return stream.readAllBytes();
        }
    }

    private static ClassLoader isolatedFacade(List<String> attempted) {
        return new ClassLoader(CuriosWirelessTerminalLookupIsolationTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    if (name.startsWith("top.theillusivec4.curios.") || name.startsWith("de.mari_023.ae2wtlib.")
                            || name.startsWith("com.lhy.wcwt.") || name.startsWith("net.minecraft.client.")
                            || name.startsWith("mezz.jei.") || name.startsWith("dev.emi.")
                            || name.equals(FACADE + "$CuriosAdapter") || name.equals(FACADE + "$Ae2wtlibAdapter")
                            || name.equals(FACADE + "$WcwtAdapter")) {
                        attempted.add(name);
                        throw new ClassNotFoundException("Optional adapter must remain unloaded: " + name);
                    }
                    if (name.equals(FACADE) || name.startsWith(FACADE + "$")) {
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
