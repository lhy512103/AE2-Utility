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

    public static boolean matchesCraftingPatternInput(AEKey input, GenericStack templateStack) {
        return matchesProviderBackedPatternInput(input, templateStack);
    }

    /**
     * 处理样板：仅当「供应该 {@link IPatternDetails} 的某一 {@link ICraftingProvider}」上装有适用的撕裂卡时才放宽。
     */
    public static boolean matchesProcessingPatternInput(AEKey input, GenericStack templateStack) {
        return matchesProviderBackedPatternInput(input, templateStack);
    }

    private static boolean matchesProviderBackedPatternInput(AEKey input, GenericStack templateStack) {
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
            NbtTearCardDebug.logPatternContext("pattern_input_precheck", pattern, cs, templateStack.what(), input,
                    pattern == null ? "pattern_null" : "crafting_service_null");
            NbtTearCardDebug.logPatternInputCheck(templateStack.what(), input, null, false, false, false);
            return false;
        }

        for (ICraftingProvider provider : cs.getProviders(pattern)) {
            if (!(provider instanceof NbtTearLogicAccess access)) {
                NbtTearCardDebug.logProviderCheck("pattern_input_provider", pattern, provider, null, null, false,
                        "provider_not_access");
                continue;
            }
            ItemStack card = access.ae2utility$getEffectiveTearCardStack();
            if (card.isEmpty() || !(card.getItem() instanceof NbtTearCardItem)) {
                NbtTearCardDebug.logProviderCheck("pattern_input_provider", pattern, provider, card, null, false,
                        "no_tear_card");
            } else {
                NbtTearFilter f = card.getOrDefault(ModDataComponents.NBT_TEAR_FILTER, NbtTearFilter.DEFAULT);
                if (NbtTearFilter.matchesUnlockExpected(templateStack.what(), input, f)) {
                    NbtTearCardDebug.logProviderCheck("pattern_input_provider", pattern, provider, card, f, true, "tear_match");
                    NbtTearCardDebug.logPatternInputCheck(templateStack.what(), input, f, false, true, true);
                    return true;
                }
                NbtTearCardDebug.logProviderCheck("pattern_input_provider", pattern, provider, card, f, false, "tear_reject");
            }
        }

        NbtTearCardDebug.logPatternContext("pattern_input_result", pattern, cs, templateStack.what(), input, "no_provider_match");
        NbtTearCardDebug.logPatternInputCheck(templateStack.what(), input, null, false, false, false);
        return false;
    }
}
