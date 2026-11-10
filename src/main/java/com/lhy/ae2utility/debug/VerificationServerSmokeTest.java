package com.lhy.ae2utility.debug;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.machine.MachineTransferProfiles;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in, isolated local-world verification; never enabled by normal server runs. */
@Mod.EventBusSubscriber(modid = Ae2UtilityMod.MOD_ID)
public final class VerificationServerSmokeTest {
    private static MinecraftServer active;
    private static int ticks;

    private VerificationServerSmokeTest() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (Boolean.getBoolean("ae2utility.serverSmokeTest") && event.getServer().isDedicatedServer()) {
            active = event.getServer();
            ticks = 0;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (active == null || active != event.getServer() || event.phase != TickEvent.Phase.END || ++ticks < 20) {
            return;
        }
        MinecraftServer server = active;
        active = null;
        if (server.overworld() == null || MachineTransferProfiles.byId("furnace") == null) {
            throw new IllegalStateException("Server smoke did not initialize world/machine metadata");
        }
        Ae2UtilityMod.LOGGER.info("SERVER_SMOKE_PASS: dedicated world loaded, ticks={}, machineProfiles={}",
                ticks, MachineTransferProfiles.all().size());
        server.halt(false);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        if (active == event.getServer()) active = null;
        ticks = 0;
    }
}
