package com.lhy.ae2utility.service;

import com.lhy.ae2utility.network.CancelClientUploadQueuesPacket;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 在服务端重置与顺序上传编排相关的会话状态，并通知客户端清空本地队列。
 */
public final class AbortUploadQueuesService {

    private AbortUploadQueuesService() {
    }

    public static void abortFor(ServerPlayer player, boolean operatorStoppedEveryone) {
        if (player == null) {
            return;
        }
        RecipeTreeUploadResultBridge.abortServerUploadOrchestration(player);
        PacketDistributor.sendToPlayer(player,
                new CancelClientUploadQueuesPacket(operatorStoppedEveryone));
    }
}
