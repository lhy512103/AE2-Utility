package com.lhy.ae2utility.integration.ae2cs;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Characterizes the actual dependency bytecode, without bootstrapping a Minecraft world. */
class Ae2CsNativeRoutingContractTest {
    private static final String LOGIC = "io/github/lounode/ae2cs/common/me/logic/ResonatingPatternProviderLogic";
    private static final String STACK = "appeng/api/stacks/GenericStack";
    private static final String ADAPTER = "appeng/helpers/patternprovider/PatternProviderTarget";

    private record Call(String owner, String name, String descriptor) {
    }

    private static Map<String, List<Call>> calls(String className) throws IOException {
        Map<String, List<Call>> methods = new HashMap<>();
        try (InputStream stream = Ae2CsNativeRoutingContractTest.class.getClassLoader()
                .getResourceAsStream(className + ".class")) {
            assertNotNull(stream, "The exact AE2CS dependency must be on the test runtime classpath");
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    List<Call> calls = new ArrayList<>();
                    methods.put(name, calls);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String method,
                                String descriptor, boolean isInterface) {
                            calls.add(new Call(owner, method, descriptor));
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return methods;
    }

    private static long count(List<Call> calls, String owner, String method) {
        return calls.stream().filter(call -> call.owner().equals(owner) && call.name().equals(method)).count();
    }

    @Test
    void singleSemanticSparseProducerPrecedesEveryNativeInsertionAndKeepsRoutingLookup() throws IOException {
        var nativeMethods = calls(LOGIC);
        var push = nativeMethods.get("pushPattern");
        Call producer = new Call(LOGIC + "$RoutedPatternData", "sparseInputs", "()Ljava/util/List;");
        Call target = new Call(LOGIC + "$RoutedPatternData", "getTargetForSparseInputIndex", "(I)Ljava/util/Optional;");
        assertEquals(1, push.stream().filter(producer::equals).count());
        assertEquals(1, push.stream().filter(target::equals).count());
        assertTrue(push.indexOf(producer) < push.indexOf(target));
        int firstInsert = push.indexOf(push.stream().filter(call -> call.owner().equals(ADAPTER)
                && call.name().equals("insert")).findFirst().orElseThrow());
        assertTrue(push.indexOf(target) < firstInsert, "Resolution and metadata remap must precede any side effects");
        assertEquals(2, count(push, LOGIC, "findTarget"), "Both native lookups must be pinned by required hooks");
        assertEquals(3, count(push, ADAPTER, "insert"), "Remote simulate + remote/local modulate");
        assertFalse(nativeMethods.containsKey("removeFromRemaining"), "1.1 hook must not be used for 1.2");
        assertEquals(0, count(push, "appeng/api/stacks/KeyCounter", "remove"),
                "The CPU, not this provider, owns holder consumption/reinjection");
    }

    @Test
    void remoteAndLocalOverflowUseActualKeysAndNativeReloadSerializesThem() throws IOException {
        var methods = calls(LOGIC);
        var push = methods.get("pushPattern");
        assertEquals(3, count(push, LOGIC + "$1Marked", "key"),
                "Remote simulation, modulation and PendingSend all read the resolved Marked key");
        assertEquals(1, count(push, LOGIC + "$PendingSend", "<init>"));
        assertEquals(1, count(push, LOGIC, "addToSendList"));
        assertEquals(3, count(push, STACK, "what"),
                "Marked construction, local modulation and local overflow read the projected GenericStack key");
        assertEquals(1, count(methods.get("writeToNBT"), STACK, "writeTag"));
        assertEquals(1, count(methods.get("readFromNBT"), STACK, "readTag"));
        assertEquals(1, count(methods.get("readFromNBT"), LOGIC + "$PendingSend", "<init>"));
    }

    @Test
    void ae2csInheritsVanillaStateCallbackSoDisconnectTailHookIsNotBypassed() throws IOException {
        assertFalse(calls(LOGIC).containsKey("onMainNodeStateChanged"),
                "An AE2CS override would need checking for delegation to the vanilla disconnect hook");
        assertTrue(calls("appeng/helpers/patternprovider/PatternProviderLogic")
                .containsKey("onMainNodeStateChanged"));
    }

    @Test
    void cpuReinjectsWholeHolderOnFalseAndReextractsOnlyAfterSuccessfulPush() throws IOException {
        var methods = calls("appeng/crafting/execution/CraftingCpuLogic");
        var push = methods.values().stream().filter(list -> count(list,
                "appeng/api/networking/crafting/ICraftingProvider", "pushPattern") > 0).findFirst().orElseThrow();
        assertEquals(1, count(push, "appeng/api/networking/crafting/ICraftingProvider", "pushPattern"));
        assertEquals(1, count(push, "appeng/crafting/execution/CraftingCpuHelper", "reinjectPatternInputs"));
        assertEquals(2, count(push, "appeng/crafting/execution/CraftingCpuHelper", "extractPatternInputs"));
    }
}
