package com.lhy.ae2utility.integration.eaep;

/** 触发 ExtendedAE-Plus 服务端下发供应器候选并打开客户端 {@code ProviderSelectScreen}。 */
public final class EaepProviderListRequest {
    private EaepProviderListRequest() {
    }

    public static boolean trySendFromClient() {
        return EaepReflection.requestProvidersListFromClient();
    }
}
