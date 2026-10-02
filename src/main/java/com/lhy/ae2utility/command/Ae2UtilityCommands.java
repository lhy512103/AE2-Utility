package com.lhy.ae2utility.command;

import java.util.Objects;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModList;

import com.lhy.ae2utility.network.CancelJeiBatchEncodeQueuePacket;
import com.lhy.ae2utility.network.ModNetworking;
import com.lhy.ae2utility.service.PendingCraftableRefreshService;

/**
 * {@code /ae2utility stopuploads}：中止执行者自己的批量上传；
 * {@code /ae2utility stopuploads all}：权限等级 4，中止全部在线玩家的批量上传。
 */
public final class Ae2UtilityCommands {
    private static final int PERMISSION_STOP_ALL_UPLOADS = 4;

    private Ae2UtilityCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ae2utility")
                .then(Commands.literal("stopuploads")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                                ctx.getSource().sendFailure(Objects.requireNonNull(
                                        Component.translatable("message.ae2utility.stopuploads_requires_player")));
                                return 0;
                            }
                            abortUploads(player);
                            ctx.getSource().sendSuccess(
                                    () -> Component.translatable("message.ae2utility.uploads_cancelled"), false);
                            return 1;
                        })
                        .then(Commands.literal("all")
                                .requires(source -> source.hasPermission(PERMISSION_STOP_ALL_UPLOADS))
                                .executes(ctx -> {
                                    var players = ctx.getSource().getServer().getPlayerList().getPlayers();
                                    for (ServerPlayer player : players) {
                                        abortUploads(player);
                                    }
                                    int count = players.size();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.translatable("message.ae2utility.stopuploads_all_done", count),
                                            true);
                                    return count;
                                }))));
    }

    private static void abortUploads(ServerPlayer player) {
        ModNetworking.sendToPlayer(player, CancelJeiBatchEncodeQueuePacket.INSTANCE);
        PendingCraftableRefreshService.clear(player);
        if (ModList.get().isLoaded("extendedae_plus")) {
            returnEaepPendingPattern(player);
        }
    }

    private static void returnEaepPendingPattern(ServerPlayer player) {
        try {
            if (com.extendedae_plus.util.uploadPattern.ProviderUploadUtil.hasPendingCtrlQPattern(player)) {
                com.extendedae_plus.util.uploadPattern.ProviderUploadUtil.returnPendingCtrlQPatternToInventory(player);
            }
        } catch (Throwable ignored) {
        }
    }
}
