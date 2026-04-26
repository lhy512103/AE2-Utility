package com.lhy.ae2utility.card;

import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.me.service.CraftingService;

import com.lhy.ae2utility.debug.NbtTearCardDebug;
import com.lhy.ae2utility.init.ModDataComponents;
import com.lhy.ae2utility.integration.ae2.NbtTearLogicAccess;
import com.lhy.ae2utility.item.NbtTearCardItem;

public final class NbtTearPatternMatchHelper {

    private NbtTearPatternMatchHelper() {
    }

    /**
     * 合成台 / 切石 / 锻造等：沿用「全网络撕裂卡合并」上下文（{@link NbtTearCraftingContext}）。
     */
    public static boolean matchesCraftingPatternInput(AEKey input, GenericStack templateStack) {
        boolean exact = input.matches(templateStack);
        if (exact) {
            NbtTearCardDebug.logPatternInputCheck(templateStack == null ? null : templateStack.what(), input, null, true, false, true);
            return true;
        }

        NbtTearFilter filter = NbtTearCraftingContext.get();
        if (filter == null || templateStack == null) {
            NbtTearCardDebug.logPatternInputCheck(templateStack == null ? null : templateStack.what(), input, filter, false, false, false);
            return false;
        }

        boolean fuzzy = NbtTearFilter.matchesUnlockExpected(templateStack.what(), input, filter);
        NbtTearCardDebug.logPatternInputCheck(templateStack.what(), input, filter, false, fuzzy, fuzzy);
        return fuzzy;
    }

    /**
     * 处理样板：仅当「供应该 {@link IPatternDetails} 的某一 {@link ICraftingProvider}」上装有适用的撕裂卡时才放宽。
     */
    public static boolean matchesProcessingPatternInput(AEKey input, GenericStack templateStack) {
        boolean exact = input.matches(templateStack);
        if (exact) {
            NbtTearCardDebug.logPatternInputCheck(templateStack == null ? null : templateStack.what(), input, null, true, false, true);
            return true;
        }
        if (templateStack == null) {
            NbtTearCardDebug.logPatternInputCheck(null, input, null, false, false, false);
            return false;
        }

        IPatternDetails pattern = NbtTearPatternContext.get();
        CraftingService cs = NbtTearSimulationEnv.getCraftingService();
        if (pattern == null || cs == null) {
            NbtTearCardDebug.logPatternInputCheck(templateStack.what(), input, null, false, false, false);
            return false;
        }

        for (ICraftingProvider provider : cs.getProviders(pattern)) {
            if (!(provider instanceof NbtTearLogicAccess access)) {
                continue;
            }
            ItemStack card = access.ae2utility$getEffectiveTearCardStack();
            if (card.isEmpty() || !(card.getItem() instanceof NbtTearCardItem)) {
                continue;
            }
            NbtTearFilter f = card.getOrDefault(ModDataComponents.NBT_TEAR_FILTER, NbtTearFilter.DEFAULT);
            if (NbtTearFilter.matchesUnlockExpected(templateStack.what(), input, f)) {
                NbtTearCardDebug.logPatternInputCheck(templateStack.what(), input, f, false, true, true);
                return true;
            }
        }

        NbtTearCardDebug.logPatternInputCheck(templateStack.what(), input, null, false, false, false);
        if (NbtTearFilter.matchesUnlockExpected(templateStack.what(), input, NbtTearFilter.DEFAULT)) {
            NbtTearProcessingMismatchNotifier.tryNotify(pattern, templateStack, input);
        }
        return false;
    }
}
