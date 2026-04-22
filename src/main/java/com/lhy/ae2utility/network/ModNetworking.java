package com.lhy.ae2utility.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.jei.MachineRecipeStateCache;
import com.lhy.ae2utility.service.MachinePullService;
import com.lhy.ae2utility.service.MachineRecipeStateService;
import com.lhy.ae2utility.service.TerminalPullService;

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
                .playToServer(PullRecipeInputsPacket.TYPE, PullRecipeInputsPacket.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> TerminalPullService.handle(context.player(), payload)));
    }
}
