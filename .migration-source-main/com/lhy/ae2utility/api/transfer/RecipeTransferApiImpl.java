package com.lhy.ae2utility.api.transfer;

import java.util.List;

import com.lhy.ae2utility.machine.MachineTransferProfile;
import com.lhy.ae2utility.machine.MachineTransferProfiles;
import com.lhy.ae2utility.network.PullMachineRecipeInputsPacket;
import com.lhy.ae2utility.network.PullRecipeInputsPacket;
import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;
import com.lhy.ae2utility.network.UniversalPullPacket;
import com.lhy.ae2utility.service.MachinePullService;
import com.lhy.ae2utility.service.TerminalPullService;
import com.lhy.ae2utility.service.UniversalPullService;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

final class RecipeTransferApiImpl implements RecipeTransferApi {
    @Override
    public RecipeTransferResult pullToOpenTerminal(ServerPlayer player, List<IngredientRequest> ingredients,
            TransferOptions options) {
        if (!valid(player, ingredients, options)) {
            return RecipeTransferResult.INVALID_REQUEST;
        }
        TerminalPullService.handle(player,
                new PullRecipeInputsPacket(options.maxTransfer(), options.craftMissing(), toInternal(ingredients)));
        return RecipeTransferResult.ACCEPTED;
    }

    @Override
    public RecipeTransferResult pullToInventory(ServerPlayer player, List<IngredientRequest> ingredients,
            TransferOptions options) {
        if (!valid(player, ingredients, options)) {
            return RecipeTransferResult.INVALID_REQUEST;
        }
        UniversalPullService.handle(player,
                new UniversalPullPacket(toInternal(ingredients), options.maxTransfer()));
        return RecipeTransferResult.ACCEPTED;
    }

    @Override
    public RecipeTransferResult pullToMachine(ServerPlayer player, ResourceLocation profileId, int containerId,
            List<IngredientRequest> ingredients, TransferOptions options) {
        if (!valid(player, ingredients, options) || profileId == null || containerId < 0) {
            return RecipeTransferResult.INVALID_REQUEST;
        }
        String internalId = MachineTransferProfiles.resolveId(profileId);
        if (internalId == null) {
            return RecipeTransferResult.WRONG_MENU;
        }
        MachinePullService.handle(player,
                new PullMachineRecipeInputsPacket(containerId, internalId, options.maxTransfer(), toInternal(ingredients)));
        return RecipeTransferResult.ACCEPTED;
    }

    @Override
    public void registerMachine(MachineTransferRegistration registration) {
        if (registration == null) {
            throw new IllegalArgumentException("registration is required");
        }
        MachineTransferProfiles.register(new MachineTransferProfile(
                registration.id().toString(),
                registration.menuClass(),
                registration.recipeType(),
                registration.inputSlotIndices()));
    }

    private static boolean valid(ServerPlayer player, List<IngredientRequest> ingredients, TransferOptions options) {
        return player != null && player.getServer() != null && player.getServer().isSameThread()
                && ingredients != null && !ingredients.isEmpty() && options != null;
    }

    private static List<RequestedIngredient> toInternal(List<IngredientRequest> ingredients) {
        return ingredients.stream()
                .map(request -> new RequestedIngredient(request.alternatives(), request.count()))
                .toList();
    }
}