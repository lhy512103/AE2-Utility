package com.lhy.ae2utility.network;




import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.jei.MachineRecipeStateCache;
import com.lhy.ae2utility.menu.NbtTearCardMenu;
import com.lhy.ae2utility.service.EaepSequentialProviderDismissHandler;
import com.lhy.ae2utility.service.InventoryPatternMatrixUploadService;
import com.lhy.ae2utility.service.InventoryPatternProviderUploadService;
import com.lhy.ae2utility.service.MachinePullService;
import com.lhy.ae2utility.service.MachineRecipeStateService;
import com.lhy.ae2utility.service.TerminalPullService;

import net.minecraft.world.entity.player.Player;

public final class ModNetworking {

    private static final String PROTOCOL = "4";
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL =
        net.minecraftforge.network.NetworkRegistry.newSimpleChannel(
            new net.minecraft.resources.ResourceLocation(Ae2UtilityMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void sendToServer(Object packet) { CHANNEL.sendToServer(packet); }
    public static void sendToPlayer(net.minecraft.server.level.ServerPlayer player, Object packet) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), packet);
    }
    public static void sendToAllPlayers(Object packet) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(), packet);
    }
    public record PayloadContext(net.minecraftforge.network.NetworkEvent.Context context) {
        public Player player() { return context.getSender(); }
        public void enqueueWork(Runnable task) { context.enqueueWork(task); }
    }
    private static final class Registrar {
        private int nextId;
        <T> Registrar playToClient(Class<T> type,
                com.lhy.ae2utility.network.codec.StreamCodec<? super net.minecraft.network.FriendlyByteBuf,T> codec,
                java.util.function.BiConsumer<T,PayloadContext> handler) {
            return add(type,codec,handler,net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
        }
        <T> Registrar playToServer(Class<T> type,
                com.lhy.ae2utility.network.codec.StreamCodec<? super net.minecraft.network.FriendlyByteBuf,T> codec,
                java.util.function.BiConsumer<T,PayloadContext> handler) {
            return add(type,codec,handler,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER);
        }
        <T> Registrar add(Class<T> type,
                com.lhy.ae2utility.network.codec.StreamCodec<? super net.minecraft.network.FriendlyByteBuf,T> codec,
                java.util.function.BiConsumer<T,PayloadContext> handler,
                net.minecraftforge.network.NetworkDirection direction) {
            CHANNEL.registerMessage(nextId++, type, (msg,buf)->codec.encode(buf,msg), codec::decode,
                (msg,supplier)->{
                    var ctx=supplier.get();
                    if(direction != net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER || ctx.getSender()!=null)
                        handler.accept(msg,new PayloadContext(ctx));
                    ctx.setPacketHandled(true);
                }, java.util.Optional.of(direction));
            return this;
        }
    }

    private ModNetworking() {
    }

    public static void register() {
        new Registrar()
                .playToClient(MachineRecipeStatePacket.class, MachineRecipeStatePacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> MachineRecipeStateCache.handle(payload)))
                .playToServer(QueryMachineRecipeStatePacket.class, QueryMachineRecipeStatePacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> MachineRecipeStateService.handle(context.player(), payload)))
                .playToServer(PullMachineRecipeInputsPacket.class, PullMachineRecipeInputsPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> MachinePullService.handle(context.player(), payload)))
                .playToServer(UniversalPullPacket.class, UniversalPullPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.lhy.ae2utility.service.UniversalPullService.handle(context.player(), payload)))
                .playToServer(EncodePatternPacket.class, EncodePatternPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.lhy.ae2utility.service.EncodePatternService.handle(context.player(), payload)))
                .playToServer(BatchEncodePatternPacket.class, BatchEncodePatternPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> {
                            if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                                com.lhy.ae2utility.service.EncodePatternService.handleBatch(sp, payload.patterns());
                            }
                        }))
                .playToServer(EaepSequentialProviderDismissPacket.class, EaepSequentialProviderDismissPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(
                                () -> EaepSequentialProviderDismissHandler.handle((net.minecraft.server.level.ServerPlayer) context.player())))
                .playToServer(ClearPatternsPacket.class, ClearPatternsPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.lhy.ae2utility.service.ClearPatternsService.handle(payload, context)))
                .playToServer(QueryCraftableStatePacket.class, QueryCraftableStatePacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.lhy.ae2utility.service.CraftableStateService.handle(context.player(), payload)))
                .playToServer(QueryEncodePreviewPacket.class, QueryEncodePreviewPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.lhy.ae2utility.service.EncodePatternService.handlePreview(context.player(), payload)))
                .playToClient(EncodePreviewResultPacket.class, EncodePreviewResultPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> EncodePreviewResultPacket.handle(payload)))
                .playToClient(SyncAe2UtilityEncodeRulesPacket.class, SyncAe2UtilityEncodeRulesPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> SyncAe2UtilityEncodeRulesPacket.handle(payload)))
                .playToClient(CraftableStatePacket.class, CraftableStatePacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.lhy.ae2utility.jei.CraftableStateCache.handle(payload)))
                .playToClient(InvalidateCraftableCachePacket.class, InvalidateCraftableCachePacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.lhy.ae2utility.jei.CraftableStateCache.invalidateKeys(payload.keys())))
                .playToClient(SequentialUploadResultPacket.class, SequentialUploadResultPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> SequentialUploadResultPacket.handle(payload)))
                .playToClient(CancelClientUploadQueuesPacket.class, CancelClientUploadQueuesPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> CancelClientUploadQueuesPacket.handle(payload)))
                .playToClient(InventoryProviderSelectionPreparedPacket.class, InventoryProviderSelectionPreparedPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> InventoryProviderSelectionPreparedPacket.handle(payload)))
                .playToClient(InventoryProviderUploadAckPacket.class, InventoryProviderUploadAckPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> InventoryProviderUploadAckPacket.handle(payload)))
                .playToClient(SyncEaepProviderSearchKeyPacket.class, SyncEaepProviderSearchKeyPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> SyncEaepProviderSearchKeyPacket.handle(payload)))
                .playToServer(UploadInventoryPatternsToMatrixPacket.class, UploadInventoryPatternsToMatrixPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> InventoryPatternMatrixUploadService.handle((net.minecraft.server.level.ServerPlayer) context.player(), payload)))
                .playToServer(PrepareInventoryProviderSelectionPacket.class, PrepareInventoryProviderSelectionPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> InventoryPatternProviderUploadService.prepareSelection(
                                (net.minecraft.server.level.ServerPlayer) context.player(), payload)))
                .playToServer(UploadInventoryPatternToProviderPacket.class, UploadInventoryPatternToProviderPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> InventoryPatternProviderUploadService.handle((net.minecraft.server.level.ServerPlayer) context.player(), payload)))
                .playToClient(FallbackToProviderSelectionPacket.class, FallbackToProviderSelectionPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.lhy.ae2utility.client.InventoryPatternUploadQueue.handleFallback(payload.slots())))
                .playToServer(PullRecipeInputsPacket.class, PullRecipeInputsPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> TerminalPullService.handle(context.player(), payload)))
                .playToServer(RedstoneSignalCardConfigApplyPacket.class, RedstoneSignalCardConfigApplyPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> RedstoneSignalCardConfigApplyPacket.applyOnServer(context.player(), payload)))
                .playToServer(NbtTearGhostSlotPacket.class, NbtTearGhostSlotPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> handleNbtTearGhostSlot(context.player(), payload)))
                .playToServer(RecipeFinderSamplePacket.class, RecipeFinderSamplePacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> handleRecipeFinderSample(context.player(), payload)))
                .playToServer(RecipeFinderEncodePacket.class, RecipeFinderEncodePacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> handleRecipeFinderEncode(context.player(), payload)));
    }

    private static void handleNbtTearGhostSlot(Player player, NbtTearGhostSlotPacket payload) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer)
                || !(player.containerMenu instanceof NbtTearCardMenu menu)) {
            return;
        }
        menu.applyGhostFilterSlot(payload.slotIndex(), payload.stack());
    }

    private static void handleRecipeFinderSample(Player player, RecipeFinderSamplePacket payload) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && serverPlayer.containerMenu instanceof com.lhy.ae2utility.menu.RecipeFinderMenu menu) {
            menu.applySample(payload.stack());
        }
    }

    private static void handleRecipeFinderEncode(Player player, RecipeFinderEncodePacket payload) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && serverPlayer.containerMenu instanceof com.lhy.ae2utility.menu.RecipeFinderMenu) {
            com.lhy.ae2utility.service.EncodePatternService.handleBatch(serverPlayer, payload.patterns());
        }
    }
}
