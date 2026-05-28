package com.lhy.ae2utility.command;

import com.lhy.ae2utility.service.AbortUploadQueuesService;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 服务端指令：需在专用服或局域网主机侧注册；玩家在聊天输入同一路径。<br>
 * {@code /ae2utility stopuploads}：仅清空<strong>命令执行玩家本人</strong>的在途上传编排；
 * {@code /ae2utility stopuploads all}
 * ：需要权限等级 4（与 Vanilla /stop、默认 OP 档位一致），对<strong>全体在线玩家</strong>执行同上中止。
 */
public final class Ae2UtilityCommands {

    /** 与 Vanilla 管理级命令对齐，防止低权限误判清全服上传。 */
    private static final int PERMISSION_STOP_ALL_UPLOADS = 4;

    private Ae2UtilityCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ae2utility")
                .then(Commands.literal("stopuploads")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                                ctx.getSource()
                                        .sendFailure(Component.translatable("message.ae2utility.stopuploads_requires_player"));
                                return 0;
                            }
                            AbortUploadQueuesService.abortFor(player, false);
                            ctx.getSource().sendSuccess(
                                    () -> Component.translatable("message.ae2utility.uploads_cancelled"),
                                    false);
                            return 1;
                        })
                        .then(Commands.literal("all")
                                .requires(cs -> cs.hasPermission(PERMISSION_STOP_ALL_UPLOADS))
                                .executes(ctx -> {
                                    var server = ctx.getSource().getServer();
                                    var list = server.getPlayerList().getPlayers();
                                    for (ServerPlayer other : list) {
                                        AbortUploadQueuesService.abortFor(other, true);
                                    }
                                    int n = list.size();
                                    ctx.getSource().sendSuccess(
                                            () -> Component.translatable("message.ae2utility.stopuploads_all_done", n),
                                            true);
                                    return n;
                                }))));
    }
}
