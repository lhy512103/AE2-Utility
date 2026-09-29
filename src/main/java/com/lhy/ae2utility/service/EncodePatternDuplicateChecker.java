package com.lhy.ae2utility.service;

import java.util.Arrays;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.stacks.GenericStack;

/**
 * 判断 ME 网络是否已登记等价样板。按样板种类、输入与输出比较，不比较 NBT，
 * 这样 EAEP 写入的「编码者」等附加标签不会让相同样板被当成不同样板。
 */
public final class EncodePatternDuplicateChecker {
    private EncodePatternDuplicateChecker() {
    }

    public static boolean networkAlreadyContains(ServerPlayer player, @Nullable IGrid grid, ItemStack encodedPattern) {
        if (grid == null || encodedPattern.isEmpty()) {
            return false;
        }
        IPatternDetails candidate = PatternDetailsHelper.decodePattern(encodedPattern, player.level());
        if (candidate == null) {
            return false;
        }
        var craftingService = grid.getCraftingService();
        for (GenericStack output : candidate.getOutputs()) {
            if (output == null || output.what() == null) {
                continue;
            }
            for (IPatternDetails existing : craftingService.getCraftingFor(output.what())) {
                if (existing != null && samePattern(candidate, existing)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean samePattern(IPatternDetails a, IPatternDetails b) {
        if (a.equals(b)) {
            return true;
        }
        if (a.getDefinition().getItem() != b.getDefinition().getItem()) {
            return false;
        }
        if (!Arrays.equals(a.getOutputs(), b.getOutputs())) {
            return false;
        }
        IPatternDetails.IInput[] inputsA = a.getInputs();
        IPatternDetails.IInput[] inputsB = b.getInputs();
        if (inputsA.length != inputsB.length) {
            return false;
        }
        for (int i = 0; i < inputsA.length; i++) {
            if (inputsA[i].getMultiplier() != inputsB[i].getMultiplier()
                    || !Arrays.equals(inputsA[i].getPossibleInputs(), inputsB[i].getPossibleInputs())) {
                return false;
            }
        }
        return true;
    }
}
