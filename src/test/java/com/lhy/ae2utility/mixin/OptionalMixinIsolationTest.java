package com.lhy.ae2utility.mixin;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

class OptionalMixinIsolationTest {
    private static final String PLUGIN = "com.lhy.ae2utility.mixin.Ae2UtilityMixinPlugin";
    private static final String MIXINS = "com.lhy.ae2utility.mixin.";
    private static final String[][] OPTIONAL_TARGETS = {
            {"mezz.jei.gui.recipes.RecipesGui", "RecipesGuiAccessor", "jei"},
            {"mezz/jei/library/gui/recipes/RecipeLayout", "MixinRecipeLayout", "jei"},
            {"com.extendedae_plus.client.screen.ProviderSelectScreen", "ProviderSelectScreenInvoker", "extendedae_plus"},
            {"net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic", "MixinAdvPatternProviderLogic", "advanced_ae"},
            {"cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic", "MixinNeoEcoCraftingCpuLogic", "neoecoae"},
            {"io/github/lounode/ae2cs/common/me/crafting/ResonatingPatternDetails$Input", "MixinResonatingPatternDetailsInput", "ae2cs"},
            {"com.moakiee.ae2lt.logic.provider.OverloadedPatternProviderLogic", "MixinOverloadedPatternProviderLogic", "ae2lt"},
            // Multi-target mixins also modify required AE2 classes, but are optional as a whole.
            {"appeng.block.crafting.PatternProviderBlock", "MixinAdvStylePatternProviderBlock", "advanced_ae"}
    };

    @Test
    void absentAndPresentMetadataDecisionsNeverLoadTargetsOrMixinClasses() throws Exception {
        List<String> attempts = new ArrayList<>();
        ClassLoader loader = isolatedPlugin(attempts);
        Class<?> pluginClass = Class.forName(PLUGIN, true, loader);
        Constructor<?> constructor = pluginClass.getDeclaredConstructor(Predicate.class);
        constructor.setAccessible(true);
        Method apply = pluginClass.getMethod("shouldApplyMixin", String.class, String.class);
        Set<String> all = Set.of("jei", "extendedae_plus", "advanced_ae", "neoecoae", "ae2cs", "ae2lt");
        for (Set<String> present : List.of(Set.<String>of(), all, Set.of("jei"), Set.of("ae2cs"))) {
            Object plugin = constructor.newInstance((Predicate<String>) present::contains);
            for (String[] target : OPTIONAL_TARGETS) {
                assertEquals(present.contains(target[2]), apply.invoke(plugin, target[0], MIXINS + target[1]),
                        target[0] + " with " + present);
            }
            assertEquals(true, apply.invoke(plugin, "appeng.me.service.CraftingService", MIXINS + "CraftingServiceAccessor"));
        }
        assertTrue(attempts.isEmpty(), "Plugin attempted to load targets: " + attempts);
    }

    @Test
    void missingLoadingMetadataFailsClosedWithoutTargetProbing() throws Exception {
        List<String> attempts = new ArrayList<>();
        Class<?> pluginClass = Class.forName(PLUGIN, true, isolatedPlugin(attempts));
        Object plugin = pluginClass.getConstructor().newInstance();
        Method apply = pluginClass.getMethod("shouldApplyMixin", String.class, String.class);
        for (String[] target : OPTIONAL_TARGETS) {
            assertEquals(false, apply.invoke(plugin, target[0], MIXINS + target[1]));
        }
        assertTrue(attempts.isEmpty(), "Plugin attempted to load targets: " + attempts);
    }

    private static ClassLoader isolatedPlugin(List<String> attempts) {
        return new ClassLoader(OptionalMixinIsolationTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    if (name.equals("net.minecraftforge.fml.loading.LoadingModList")) {
                        throw new ClassNotFoundException("No Forge metadata initialized");
                    }
                    if (name.equals(PLUGIN)) {
                        Class<?> loaded = findLoadedClass(name);
                        if (loaded == null) {
                            try (InputStream stream = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                                assertNotNull(stream);
                                byte[] bytes = stream.readAllBytes();
                                loaded = defineClass(name, bytes, 0, bytes.length);
                            } catch (IOException e) {
                                throw new ClassNotFoundException(name, e);
                            }
                        }
                        if (resolve) resolveClass(loaded);
                        return loaded;
                    }
                    if (name.startsWith("mezz.jei.") || name.startsWith("dev.emi.")
                            || name.startsWith("com.extendedae_plus.") || name.startsWith("net.pedroksl.")
                            || name.startsWith("cn.dancingsnow.") || name.startsWith("io.github.lounode.")
                            || name.startsWith("com.moakiee.") || name.startsWith("appeng.")
                            || name.startsWith(MIXINS)) {
                        attempts.add(name);
                        throw new ClassNotFoundException("Mixin targets must not be loaded: " + name);
                    }
                    return super.loadClass(name, resolve);
                }
            }
        };
    }
}
