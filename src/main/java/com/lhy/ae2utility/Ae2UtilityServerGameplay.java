package com.lhy.ae2utility;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import com.lhy.ae2utility.network.SyncAe2UtilityEncodeRulesPacket;
import com.lhy.ae2utility.service.EncodeBulkSessionLimiter;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 登录下发编码策略包、离线清理服务端限量状态；服务端配置 reload 后对在线玩家刷新规则。
 */
public final class Ae2UtilityServerGameplay {

    private Ae2UtilityServerGameplay() {
    }

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, Ae2UtilityServerGameplay::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, Ae2UtilityServerGameplay::onPlayerLogout);
        modBus.addListener(ModConfigEvent.Reloading.class, Ae2UtilityServerGameplay::onModConfigReload);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            sendEncodeRules(sp);
        }
    }

    private static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EncodeBulkSessionLimiter.clearFor(event.getEntity().getUUID());
    }

    private static void onModConfigReload(ModConfigEvent.Reloading ev) {
        if (!Ae2UtilityMod.MOD_ID.equals(ev.getConfig().getModId())) {
            return;
        }
        if (ev.getConfig().getType() != ModConfig.Type.SERVER) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            sendEncodeRules(sp);
        }
    }

    public static void sendEncodeRules(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new SyncAe2UtilityEncodeRulesPacket(
                        Ae2UtilityServerConfig.blockJeiFullCategoryBatchEncode(),
                        Ae2UtilityServerConfig.requireOpenPatternEncodingMenuForJei(),
                        Ae2UtilityServerConfig.jeiBulkEncodeMaxPatternsPerSession()));
    }
}
