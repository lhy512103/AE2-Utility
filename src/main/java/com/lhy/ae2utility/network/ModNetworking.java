package com.lhy.ae2utility.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.jei.MachineRecipeStateCache;
import com.lhy.ae2utility.menu.NbtTearCardMenu;
import com.lhy.ae2utility.service.InventoryPatternMatrixUploadService;
import com.lhy.ae2utility.service.InventoryPatternProviderUploadService;
import com.lhy.ae2utility.service.MachinePullService;
import com.lhy.ae2utility.service.MachineRecipeStateService;
import com.lhy.ae2utility.service.TerminalPullService;

import net.minecraft.world.entity.player.Player;

public final class ModNetworking {
    private ModNetworking() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(Ae2UtilityMod.MOD_ID)
                .executesOn(HandlerThread.MAIN)
                .playToClient(MachineRecipeStatePacket.TYPE, MachineRecipeStatePacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> MachineRecipeStateCache.handle(payload)))
                .playToServer(QueryMachineRecipeStatePacket.TYPE, QueryMachineRecipeStatePacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> MachineRecipeStateService.handle(context.player(), payload)))
                .playToServer(PullMachineRecipeInputsPacket.TYPE, PullMachineRecipeInputsPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> MachinePullService.handle(context.player(), payload)))
                .playToServer(UniversalPullPacket.TYPE, UniversalPullPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.lhy.ae2utility.service.UniversalPullService.handle(context.player(), payload)))
                .playToServer(EncodePatternPacket.TYPE, EncodePatternPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.lhy.ae2utility.service.EncodePatternService.handle(context.player(), payload)))
                .playToServer(ClearPatternsPacket.TYPE, ClearPatternsPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.lhy.ae2utility.service.ClearPatternsService.handle(payload, context)))
                .playToServer(QueryCraftableStatePacket.TYPE, QueryCraftableStatePacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.lhy.ae2utility.service.CraftableStateService.handle(context.player(), payload)))
                .playToClient(CraftableStatePacket.TYPE, CraftableStatePacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.lhy.ae2utility.jei.CraftableStateCache.handle(payload)))
                .playToClient(InvalidateCraftableCachePacket.TYPE, InvalidateCraftableCachePacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.lhy.ae2utility.jei.CraftableStateCache.invalidateKeys(payload.keys())))
                .playToClient(RecipeTreeUploadResultPacket.TYPE, RecipeTreeUploadResultPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> RecipeTreeUploadResultPacket.handle(payload)))
                .playToClient(RecipeTreeOpenProvidersPacket.TYPE, RecipeTreeOpenProvidersPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(RecipeTreeOpenProvidersPacket::handle))
                .playToServer(UploadInventoryPatternsToMatrixPacket.TYPE, UploadInventoryPatternsToMatrixPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> InventoryPatternMatrixUploadService.handle((net.minecraft.server.level.ServerPlayer) context.player(), payload)))
                .playToServer(UploadInventoryPatternToProviderPacket.TYPE, UploadInventoryPatternToProviderPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> InventoryPatternProviderUploadService.handle((net.minecraft.server.level.ServerPlayer) context.player(), payload)))
                .playToClient(FallbackToProviderSelectionPacket.TYPE, FallbackToProviderSelectionPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> com.lhy.ae2utility.client.InventoryPatternUploadQueue.handleFallback(payload.slots())))
                .playToServer(PullRecipeInputsPacket.TYPE, PullRecipeInputsPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> TerminalPullService.handle(context.player(), payload)))
                .playToServer(NbtTearGhostSlotPacket.TYPE, NbtTearGhostSlotPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> handleNbtTearGhostSlot(context.player(), payload)));
    }

    private static void handleNbtTearGhostSlot(Player player, NbtTearGhostSlotPacket payload) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer)
                || !(player.containerMenu instanceof NbtTearCardMenu menu)) {
            return;
        }
        menu.applyGhostFilterSlot(payload.slotIndex(), payload.stack());
    }
}
