package com.lhy.ae2utility.integration.eaep;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

/** 触发 ExtendedAE-Plus 服务端下发供应器候选并打开客户端 {@code ProviderSelectScreen}。 */
public final class EaepProviderListRequest {
    private static final String PACKET_CLASS = "com.extendedae_plus.network.RequestProvidersListC2SPacket";

    private EaepProviderListRequest() {
    }

    public static boolean trySendFromClient() {
        if (!ModList.get().isLoaded("extendedae_plus")) {
            return false;
        }
        try {
            Class<?> cls = Class.forName(PACKET_CLASS);
            Object packet = cls.getDeclaredField("INSTANCE").get(null);
            PacketDistributor.sendToServer((net.minecraft.network.protocol.common.custom.CustomPacketPayload) packet);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
