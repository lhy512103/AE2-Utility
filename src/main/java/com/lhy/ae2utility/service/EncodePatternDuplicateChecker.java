package com.lhy.ae2utility.service;

import java.util.Collection;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.me.service.CraftingService;
import appeng.me.service.helpers.NetworkCraftingProviders;

import com.lhy.ae2utility.integration.eaep.EaepReflection;
import com.lhy.ae2utility.mixin.CraftingServiceAccessor;

public final class EncodePatternDuplicateChecker {
    private EncodePatternDuplicateChecker() {
    }

    /**
     * JEI 批量编码：终端所在 ME 网络是否已登记等价样板（供应器 / 矩阵等）。
     */
    public static boolean batchNetworkAlreadyContains(ServerPlayer player, @Nullable IGrid grid, ItemStack encodedCandidate) {
        if (encodedCandidate.isEmpty() || grid == null) {
            return false;
        }
        if (ModList.get().isLoaded("extendedae_plus")) {
            if (EaepReflection.matrixContainsPattern(grid, encodedCandidate)) {
                return true;
            }
        }

        IPatternDetails cand = PatternDetailsHelper.decodePattern(encodedCandidate, player.level());
        if (cand == null) {
            return false;
        }
        if (!(grid.getCraftingService() instanceof CraftingService cs)) {
            return false;
        }
        NetworkCraftingProviders providers = ((CraftingServiceAccessor) (Object) cs).ae2utility$getCraftingProviders();
        for (appeng.api.stacks.GenericStack out : cand.getOutputs()) {
            if (out == null || out.what() == null) {
                continue;
            }
            Collection<IPatternDetails> existingCol = providers.getCraftingFor(out.what());
            if (existingCol == null || existingCol.isEmpty()) {
                continue;
            }
            for (IPatternDetails existing : existingCol) {
                if (existing != null && Objects.equals(cand, existing)) {
                    return true;
                }
            }
        }
        return false;
    }
}
