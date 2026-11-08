package com.lhy.ae2utility;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.common.MinecraftForge;
import com.lhy.ae2utility.network.ModNetworking;
import net.minecraftforge.server.ServerLifecycleHooks;

import com.lhy.ae2utility.network.SyncAe2UtilityEncodeRulesPacket;
import com.lhy.ae2utility.service.EncodeBulkSessionLimiter;
import com.lhy.ae2utility.service.MeQuickTransferService;

import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * 登录下发编码策略包、离线清理服务端限量状态；服务端配置 reload 后对在线玩家刷新规则。
 */
public final class Ae2UtilityServerGameplay {

    private Ae2UtilityServerGameplay() {
    }

    public static void register(IEventBus modBus) {
        MinecraftForge.EVENT_BUS.addListener(Ae2UtilityServerGameplay::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(Ae2UtilityServerGameplay::onPlayerLogout);
        modBus.addListener(Ae2UtilityServerGameplay::onModConfigReload);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            sendEncodeRules(sp);
            MeQuickTransferService.cancel(sp.getUUID());
        }
    }

    private static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        EncodeBulkSessionLimiter.clearFor(event.getEntity().getUUID());
        MeQuickTransferService.cancel(event.getEntity().getUUID());
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
        ModNetworking.sendToPlayer(player,
                new SyncAe2UtilityEncodeRulesPacket(
                        Ae2UtilityServerConfig.blockJeiFullCategoryBatchEncode(),
                        Ae2UtilityServerConfig.requireOpenPatternEncodingMenuForJei(),
                        Ae2UtilityServerConfig.jeiBulkEncodeMaxPatternsPerSession()));
    }
}
