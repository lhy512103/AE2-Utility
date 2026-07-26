package com.lhy.ae2utility.api.transfer;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Server transfer operations and client machine-profile registration. */
public interface RecipeTransferApi {
    RecipeTransferApi INSTANCE = new RecipeTransferApiImpl();

    RecipeTransferResult pullToOpenTerminal(ServerPlayer player, List<IngredientRequest> ingredients,
            TransferOptions options);

    RecipeTransferResult pullToInventory(ServerPlayer player, List<IngredientRequest> ingredients,
            TransferOptions options);

    RecipeTransferResult pullToMachine(ServerPlayer player, ResourceLocation profileId, int containerId,
            List<IngredientRequest> ingredients, TransferOptions options);

    void registerMachine(MachineTransferRegistration registration);
}