package com.lhy.ae2utility.service;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.stacks.AEItemKey;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.machine.MachineTransferProfiles;
import com.lhy.ae2utility.network.MachineRecipeStatePacket;
import com.lhy.ae2utility.network.MachineRecipeStatePacket.IngredientAvailability;
import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;
import com.lhy.ae2utility.network.QueryMachineRecipeStatePacket;
import com.lhy.ae2utility.network.RecipeTransferPacketHelper;

public final class MachineRecipeStateService {
    private MachineRecipeStateService() {
    }

    public static void handle(Player player, QueryMachineRecipeStatePacket payload) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        String requestKey = RecipeTransferPacketHelper.requestSignature(payload.requestedIngredients());

        var profile = MachineTransferProfiles.byId(payload.profileId());
        if (profile == null || serverPlayer.containerMenu.containerId != payload.containerId() || !profile.matches(serverPlayer.containerMenu)) {
            return;
        }

        var resolution = WirelessTerminalContextResolver.resolve(serverPlayer);
        if (!resolution.isReady()) {
            PacketDistributor.sendToPlayer(serverPlayer, new MachineRecipeStatePacket(
                    payload.containerId(),
                    payload.profileId(),
                    requestKey,
                    toPacketState(resolution.status()),
                    List.of()));
            return;
        }

        var host = resolution.host();
        var storage = host.getInventory().getAvailableStacks();
        List<IngredientAvailability> availability = new ArrayList<>();
        for (RequestedIngredient ingredient : payload.requestedIngredients()) {
            if (ingredient.count() <= 0 || ingredient.alternatives().isEmpty()) {
                continue;
            }
            for (ItemStack alternative : ingredient.alternatives()) {
                if (alternative.isEmpty() || containsEquivalent(availability, alternative)) {
                    continue;
                }

                var key = AEItemKey.of(alternative);
                if (key == null) {
                    continue;
                }

                long amount = storage.get(key);
                if (amount <= 0) {
                    continue;
                }

                availability.add(new IngredientAvailability(alternative.copy(),
                        (int) Math.min(Integer.MAX_VALUE, amount)));
            }
        }

        PacketDistributor.sendToPlayer(serverPlayer, new MachineRecipeStatePacket(
                payload.containerId(),
                payload.profileId(),
                requestKey,
                MachineRecipeStatePacket.State.READY,
                availability));
    }

    private static boolean containsEquivalent(List<IngredientAvailability> availability, ItemStack stack) {
        for (IngredientAvailability entry : availability) {
            if (ItemStack.isSameItemSameComponents(entry.stack(), stack)) {
                return true;
            }
        }
        return false;
    }

    private static MachineRecipeStatePacket.State toPacketState(WirelessTerminalContextResolver.Status status) {
        return switch (status) {
            case READY -> MachineRecipeStatePacket.State.READY;
            case NO_WIRELESS -> MachineRecipeStatePacket.State.NO_WIRELESS;
            case DISCONNECTED -> MachineRecipeStatePacket.State.DISCONNECTED;
        };
    }
}
