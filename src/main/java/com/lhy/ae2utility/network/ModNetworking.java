package com.lhy.ae2utility.network;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.jei.CraftableStateCache;
import com.lhy.ae2utility.service.ClearPatternsService;
import com.lhy.ae2utility.service.CraftableStateService;
import com.lhy.ae2utility.service.EncodePatternService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class ModNetworking {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Ae2UtilityMod.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int nextId = 0;

    private ModNetworking() {
    }

    public static void register() {
        CHANNEL.registerMessage(nextId++, EncodePatternPacket.class,
                EncodePatternPacket::encode, EncodePatternPacket::decode,
                (msg, ctx) -> handleEncode(msg, ctx.get()),
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(nextId++, QueryCraftableStatePacket.class,
                QueryCraftableStatePacket::encode, QueryCraftableStatePacket::decode,
                (msg, ctx) -> handleQueryCraftable(msg, ctx.get()),
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(nextId++, CraftableStatePacket.class,
                CraftableStatePacket::encode, CraftableStatePacket::decode,
                (msg, ctx) -> handleCraftableState(msg, ctx.get()),
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(nextId++, CancelJeiBatchEncodeQueuePacket.class,
                CancelJeiBatchEncodeQueuePacket::encode, CancelJeiBatchEncodeQueuePacket::decode,
                (msg, ctx) -> handleCancelJeiBatchEncodeQueue(msg, ctx.get()),
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(nextId++, OpenEaepProviderSelectionPacket.class,
                OpenEaepProviderSelectionPacket::encode, OpenEaepProviderSelectionPacket::decode,
                (msg, ctx) -> handleOpenEaepProviderSelection(msg, ctx.get()),
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(nextId++, ClearPatternsPacket.class,
                ClearPatternsPacket::encode, ClearPatternsPacket::decode,
                (msg, ctx) -> handleClearPatterns(msg, ctx.get()),
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendToServer(Object payload) {
        CHANNEL.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, Object payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    private static void handleEncode(EncodePatternPacket msg, NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getSender() != null) {
                EncodePatternService.handle(ctx.getSender(), msg);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void handleQueryCraftable(QueryCraftableStatePacket msg, net.minecraftforge.network.NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getSender() != null) {
                CraftableStateService.handle(ctx.getSender(), msg);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void handleCraftableState(CraftableStatePacket msg, NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> CraftableStateCache.handle(msg));
        ctx.setPacketHandled(true);
    }

    private static void handleCancelJeiBatchEncodeQueue(CancelJeiBatchEncodeQueuePacket msg, NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> CancelJeiBatchEncodeQueuePacket.handle(msg));
        ctx.setPacketHandled(true);
    }

    private static void handleOpenEaepProviderSelection(OpenEaepProviderSelectionPacket msg, NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> OpenEaepProviderSelectionPacket.handle(msg));
        ctx.setPacketHandled(true);
    }

    private static void handleClearPatterns(ClearPatternsPacket msg, NetworkEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getSender() != null) {
                ClearPatternsService.handle(ctx.getSender(), msg);
            }
        });
        ctx.setPacketHandled(true);
    }
}
