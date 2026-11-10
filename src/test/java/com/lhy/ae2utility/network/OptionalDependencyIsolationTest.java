package com.lhy.ae2utility.network;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** Denies optional/client classes even though they are present on the development classpath. */
class OptionalDependencyIsolationTest {
    private static final String ROOT = "com.lhy.ae2utility.";
    private static final List<String> COMMON_PACKETS = List.of(
            "PullRecipeInputsPacket", "PullMachineRecipeInputsPacket", "UniversalPullPacket",
            "QueryMachineRecipeStatePacket", "MachineRecipeStatePacket", "CraftableStatePacket",
            "InvalidateCraftableCachePacket", "EncodePreviewResultPacket", "SyncAe2UtilityEncodeRulesPacket",
            "SequentialUploadResultPacket", "CancelClientUploadQueuesPacket",
            "InventoryProviderSelectionPreparedPacket", "InventoryProviderUploadAckPacket",
            "SyncEaepProviderSearchKeyPacket", "FallbackToProviderSelectionPacket");

    @Test
    void commonEntrypointsAndPacketSignaturesResolveWithoutClientOrOptionalClasses() throws Exception {
        ClassLoader loader = isolatedServer();
        for (String name : COMMON_PACKETS) {
            Class<?> type = Class.forName(ROOT + "network." + name, true, loader);
            assertNotNull(type.getField("STREAM_CODEC").get(null));
            type.getDeclaredMethods();
            type.getDeclaredConstructors();
        }
        // Loading/reflection is intentionally separate from Forge bootstrap and channel creation.
        for (String name : List.of("Ae2UtilityMod", "Ae2UtilityServerGameplay", "network.ModNetworking",
                "network.ModNetworking$PayloadContext", "network.RequestedIngredientsCodec",
                "api.transfer.RecipeTransferApi", "api.transfer.RecipeTransferApiImpl",
                "api.transfer.MachineTransferRegistration", "api.transfer.MachineTransferClientAdapter",
                "api.Ae2UtilityApi", "api.pattern.PatternEncodingApi", "api.pattern.PatternEncodingApiImpl",
                "api.card.CardApi", "api.card.CardApiImpl", "compat.WcwtCompat", "util.RawIngredientUtil")) {
            Class<?> type = Class.forName(ROOT + name, false, loader);
            type.getDeclaredMethods();
            type.getDeclaredFields();
            type.getDeclaredConstructors();
        }
    }

    @Test
    void serverTransferApiInitializesAndRejectsInvalidRequestsWithoutViewers() throws Exception {
        ClassLoader loader = isolatedServer();
        Class<?> api = Class.forName(ROOT + "api.transfer.RecipeTransferApi", true, loader);
        Object instance = api.getField("INSTANCE").get(null);
        for (Method method : api.getMethods()) {
            assertFalse(method.getReturnType().getName().startsWith("mezz.jei."));
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(parameter.getName().startsWith("mezz.jei."));
            }
            if (!method.getName().startsWith("pullTo")) continue;
            Object result = method.invoke(instance, defaultArgs(method));
            assertEquals("INVALID_REQUEST", result.toString());
        }
        Class<?> encoding = Class.forName(ROOT + "api.pattern.PatternEncodingApi", true, loader);
        Object encodingInstance = encoding.getField("INSTANCE").get(null);
        for (Method method : encoding.getMethods()) {
            if (!method.getName().startsWith("encode")) continue;
            assertEquals("INVALID_REQUEST", method.invoke(encodingInstance, defaultArgs(method)).toString());
        }
        Class<?> card = Class.forName(ROOT + "api.card.CardApi", true, loader);
        assertNotNull(card.getField("INSTANCE").get(null));
    }

    @Test
    void pullPacketCodecsExecuteWithoutJeiHelperAndPreserveWireLayout() throws Exception {
        ClassLoader loader = isolatedServer();
        Object[][] cases = {
                {"PullRecipeInputsPacket", new Class<?>[]{boolean.class, boolean.class, List.class},
                        new Object[]{true, false, List.of()}},
                {"PullMachineRecipeInputsPacket", new Class<?>[]{int.class, String.class, boolean.class, List.class},
                        new Object[]{17, "furnace", true, List.of()}},
                {"QueryMachineRecipeStatePacket", new Class<?>[]{int.class, String.class, List.class},
                        new Object[]{17, "furnace", List.of()}},
                {"UniversalPullPacket", new Class<?>[]{List.class, boolean.class},
                        new Object[]{List.of(), true}}
        };
        for (Object[] entry : cases) {
            Class<?> type = Class.forName(ROOT + "network." + entry[0], true, loader);
            Object packet = type.getConstructor((Class<?>[]) entry[1]).newInstance((Object[]) entry[2]);
            Object codec = type.getField("STREAM_CODEC").get(null);
            Class<?> codecType = loader.loadClass(ROOT + "network.codec.StreamCodec");
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                codecType.getMethod("encode", Object.class, Object.class).invoke(codec, buffer, packet);
                Object decoded = codecType.getMethod("decode", Object.class).invoke(codec, buffer);
                assertEquals(packet, decoded);
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
        Class<?> codec = Class.forName(ROOT + "network.RequestedIngredientsCodec", true, loader);
        assertEquals("", codec.getMethod("requestSignature", List.class).invoke(null, List.of()));
        assertEquals(true, codec.getMethod("canEncodeRequestedIngredients", List.class).invoke(null, List.of()));
    }

    @Test
    void s2cDispatchIsInertOnServerAndRoutesOnlyExplicitlyInstalledHandlers() throws Exception {
        ClassLoader loader = isolatedServer();
        Class<?> bridge = Class.forName(ROOT + "network.ClientPacketDispatch", true, loader);
        bridge.getMethod("handle", Object.class).invoke(null, "no client installed");
        AtomicReference<Object> received = new AtomicReference<>();
        Consumer<Object> handler = received::set;
        bridge.getMethod("register", Class.class, Consumer.class).invoke(null, String.class, handler);
        bridge.getMethod("handle", Object.class).invoke(null, "installed");
        assertEquals("installed", received.get());
        bridge.getMethod("handle", Object.class).invoke(null, 42);
        assertEquals("installed", received.get());
        for (String name : List.of("CancelClientUploadQueuesPacket", "SyncEaepProviderSearchKeyPacket")) {
            Class<?> packetType = Class.forName(ROOT + "network." + name, true, loader);
            Class<?>[] types = name.startsWith("Cancel") ? new Class<?>[]{boolean.class}
                    : new Class<?>[]{boolean.class, String.class};
            Object[] args = name.startsWith("Cancel") ? new Object[]{true} : new Object[]{true, "test"};
            Object packet = packetType.getConstructor(types).newInstance(args);
            packetType.getMethod("handle", packetType).invoke(null, packet);
        }
    }

    @Test
    void viewerNeutralPreviewDispatchRunsWithoutJeiForEmiOnlyClients() throws Exception {
        ClassLoader loader = isolatedServer();
        Class<?> packetType = Class.forName(ROOT + "network.EncodePreviewResultPacket", true, loader);
        Object packet = packetType.getMethod("empty", int.class).invoke(null, 23);
        Class<?> dispatch = Class.forName(ROOT + "client.EncodePreviewClientDispatch", true, loader);
        AtomicReference<Object> received = new AtomicReference<>();
        dispatch.getMethod("addListener", Consumer.class).invoke(null, (Consumer<Object>) received::set);
        dispatch.getMethod("handle", packetType).invoke(null, packet);
        assertSame(packet, received.get());
    }

    @Test
    void commonPacketAndApiBytecodeCannotRegressToOptionalImplementations() throws IOException {
        List<String> names = new ArrayList<>(COMMON_PACKETS.stream().map(name -> "network." + name).toList());
        names.addAll(List.of("network.ModNetworking", "network.ClientPacketDispatch",
                "network.RequestedIngredientsCodec", "service.MachineRecipeStateService",
                "api.transfer.RecipeTransferApiImpl", "api.transfer.MachineTransferClientAdapter",
                "api.pattern.PatternEncodingApiImpl", "api.card.CardApiImpl", "compat.WcwtCompat", "util.RawIngredientUtil"));
        for (String name : names) {
            String bytecode = new String(classBytes(ROOT + name), StandardCharsets.ISO_8859_1);
            for (String forbidden : List.of("mezz/jei/", "dev/emi/", "net/minecraft/client/",
                    "com/lhy/ae2utility/client/", "com/lhy/ae2utility/jei/",
                    "com/lhy/ae2utility/network/RecipeTransferPacketHelper")) {
                assertFalse(bytecode.contains(forbidden), name + " links " + forbidden);
            }
        }
    }

    private static byte[] classBytes(String name) throws IOException {
        try (InputStream stream = OptionalDependencyIsolationTest.class.getClassLoader()
                .getResourceAsStream(name.replace('.', '/') + ".class")) {
            assertNotNull(stream, name);
            return stream.readAllBytes();
        }
    }

    /** Null for references, zero/false for primitives, so reflective calls reach argument validation. */
    private static Object[] defaultArgs(Method method) {
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            if (!t.isPrimitive()) continue;
            // Separate statements: a chained ternary would promote every boxed value to Double.
            if (t == boolean.class) args[i] = false;
            else if (t == char.class) args[i] = ' ';
            else if (t == long.class) args[i] = 0L;
            else if (t == float.class) args[i] = 0F;
            else if (t == double.class) args[i] = 0D;
            else if (t == byte.class) args[i] = (byte) 0;
            else if (t == short.class) args[i] = (short) 0;
            else args[i] = 0;
        }
        return args;
    }

    private static ClassLoader isolatedServer() {
        return new ClassLoader(OptionalDependencyIsolationTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    if (name.startsWith("mezz.jei.") || name.startsWith("dev.emi.")
                            || name.startsWith("net.minecraft.client.") || name.startsWith("net.minecraftforge.client.")
                            || (name.startsWith(ROOT + "client.")
                                    && !name.equals(ROOT + "client.EncodePreviewClientDispatch"))
                            || name.startsWith(ROOT + "jei.")
                            || name.equals(ROOT + "network.RecipeTransferPacketHelper")
                            || name.startsWith("com.extendedae_plus.") || name.startsWith("mekanism.")
                            || name.startsWith("net.pedroksl.") || name.startsWith("com.glodblock.github.")
                            || name.startsWith("io.github.lounode.ae2cs.") || name.startsWith("com.moakiee.")) {
                        throw new ClassNotFoundException("Not available on isolated base server: " + name);
                    }
                    if (name.startsWith(ROOT)) {
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
