package com.lhy.ae2utility.testutil;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/**
 * Bootstraps vanilla registries once for plain JUnit runs.
 *
 * <p>Outside FML the EventBus transformer does not run, so Forge's network handshake setup at the
 * end of {@link Bootstrap#bootStrap()} fails. Vanilla registries are already populated by then;
 * tests here never touch Forge networking, so that single failure is tolerated.</p>
 */
public final class MinecraftTestBootstrap {
    private static boolean done;

    private MinecraftTestBootstrap() {}

    public static synchronized void ensure() {
        if (done) return;
        SharedConstants.tryDetectVersion();
        try {
            Bootstrap.bootStrap();
        } catch (ExceptionInInitializerError e) {
            if (!fromForgeNetworkInit(e)) throw e;
        }
        done = true;
    }

    private static boolean fromForgeNetworkInit(Throwable error) {
        for (StackTraceElement frame : error.getStackTrace()) {
            if (frame.getClassName().startsWith("net.minecraftforge.network.")) return true;
        }
        return false;
    }
}
