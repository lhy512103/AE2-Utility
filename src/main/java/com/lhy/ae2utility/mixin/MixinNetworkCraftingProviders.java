package com.lhy.ae2utility.mixin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.ItemStack;

import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.AEKeyFilter;
import appeng.me.service.helpers.NetworkCraftingProviders;

import com.lhy.ae2utility.card.NbtTearFilter;
import com.lhy.ae2utility.debug.NbtTearCardDebug;
import com.lhy.ae2utility.init.ModDataComponents;
import com.lhy.ae2utility.integration.ae2.NbtTearLogicAccess;
import com.lhy.ae2utility.item.NbtTearCardItem;

@Mixin(value = NetworkCraftingProviders.class, remap = false)
public abstract class MixinNetworkCraftingProviders {

    @Shadow
    private KeyCounter craftableItemsList;

    @Shadow
    public abstract Collection<IPatternDetails> getCraftingFor(AEKey whatToCraft);

    @Shadow
    public abstract Iterable<ICraftingProvider> getMediums(IPatternDetails key);

    @Unique
    private static final ThreadLocal<Boolean> ae2utility$isInsideFuzzy = ThreadLocal.withInitial(() -> false);

    /**
     * When no exact pattern is found for {@code whatToCraft}, try a fuzzy item lookup
     * (same item type, any NBT). For each candidate pattern, verify at least one provider
     * allows the substitution via its tear card.
     */
    @Inject(method = "getCraftingFor", at = @At("RETURN"), cancellable = true)
    private void ae2utility$fuzzyGetCraftingFor(AEKey whatToCraft, CallbackInfoReturnable<Collection<IPatternDetails>> cir) {
        if (ae2utility$isInsideFuzzy.get()) return;
        if (!cir.getReturnValue().isEmpty()) return;

        ae2utility$isInsideFuzzy.set(true);
        try {
            List<IPatternDetails> fuzzyPatterns = new ArrayList<>();
            for (var fuzzy : craftableItemsList.findFuzzy(whatToCraft, FuzzyMode.IGNORE_ALL)) {
                AEKey candidateKey = fuzzy.getKey();
                if (candidateKey.equals(whatToCraft)) continue;

                NbtTearFilter filter = ae2utility$getFilterForCandidate(candidateKey, whatToCraft);
                if (filter == null) {
                    NbtTearCardDebug.logFuzzyCraftSearch("getCraftingFor", whatToCraft, candidateKey, false, "filter_null");
                    continue;
                }
                NbtTearCardDebug.logFuzzyCraftSearch("getCraftingFor", whatToCraft, candidateKey, true, "filter_match");

                for (IPatternDetails pattern : this.getCraftingFor(candidateKey)) {
                    fuzzyPatterns.add(pattern);
                }
            }
            if (!fuzzyPatterns.isEmpty()) {
                cir.setReturnValue(fuzzyPatterns);
            }
        } finally {
            ae2utility$isInsideFuzzy.set(false);
        }
    }

    @Inject(method = "getFuzzyCraftable", at = @At("RETURN"), cancellable = true)
    private void ae2utility$getFuzzyCraftable(AEKey whatToCraft, AEKeyFilter filter, CallbackInfoReturnable<AEKey> cir) {
        if (ae2utility$isInsideFuzzy.get()) return;
        if (cir.getReturnValue() != null) return;

        ae2utility$isInsideFuzzy.set(true);
        try {
            for (var fuzzy : craftableItemsList.findFuzzy(whatToCraft, FuzzyMode.IGNORE_ALL)) {
                AEKey candidateKey = fuzzy.getKey();
                if (candidateKey.equals(whatToCraft)) continue;
                if (!filter.matches(candidateKey)) {
                    NbtTearCardDebug.logFuzzyCraftSearch("getFuzzyCraftable", whatToCraft, candidateKey, false, "ae2_filter_reject");
                    continue;
                }

                NbtTearFilter tearFilter = ae2utility$getFilterForCandidate(candidateKey, whatToCraft);
                if (tearFilter != null) {
                    NbtTearCardDebug.logFuzzyCraftSearch("getFuzzyCraftable", whatToCraft, candidateKey, true, "tear_filter_match");
                    cir.setReturnValue(candidateKey);
                    return;
                }
                NbtTearCardDebug.logFuzzyCraftSearch("getFuzzyCraftable", whatToCraft, candidateKey, false, "tear_filter_reject");
            }
        } finally {
            ae2utility$isInsideFuzzy.set(false);
        }
    }

    /**
     * Returns the first applicable {@link NbtTearFilter} that authorises treating
     * {@code candidateKey} as a substitute for {@code whatToCraft}, or {@code null}.
     */
    @Unique
    private NbtTearFilter ae2utility$getFilterForCandidate(AEKey candidateKey, AEKey whatToCraft) {
        for (IPatternDetails pattern : this.getCraftingFor(candidateKey)) {
            for (ICraftingProvider provider : this.getMediums(pattern)) {
                if (!(provider instanceof NbtTearLogicAccess access)) {
                    NbtTearCardDebug.logFuzzyCraftSearch("provider_scan", whatToCraft, provider, false, "provider_not_access");
                    continue;
                }
                ItemStack card = access.ae2utility$getEffectiveTearCardStack();
                if (card.isEmpty() || !(card.getItem() instanceof NbtTearCardItem)) {
                    NbtTearCardDebug.logFuzzyCraftSearch("provider_scan", whatToCraft, provider, false, "no_tear_card");
                    continue;
                }
                NbtTearFilter f = card.getOrDefault(ModDataComponents.NBT_TEAR_FILTER, NbtTearFilter.DEFAULT);
                if (NbtTearFilter.matchesUnlockExpected(candidateKey, whatToCraft, f)) {
                    NbtTearCardDebug.logFuzzyCraftSearch("provider_scan", whatToCraft, provider, true, "tear_filter_match");
                    return f;
                }
                NbtTearCardDebug.logFuzzyCraftSearch("provider_scan", whatToCraft, provider, false, "tear_filter_reject");
            }
        }
        return null;
    }
}
