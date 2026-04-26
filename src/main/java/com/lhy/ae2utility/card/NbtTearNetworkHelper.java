package com.lhy.ae2utility.card;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import appeng.me.service.helpers.NetworkCraftingProviders;

import com.lhy.ae2utility.debug.NbtTearCardDebug;
import com.lhy.ae2utility.integration.ae2.NbtTearLogicAccess;
import com.lhy.ae2utility.item.NbtTearCardItem;
import com.lhy.ae2utility.mixin.CraftingServiceAccessor;
import com.lhy.ae2utility.init.ModDataComponents;

/**
 * 合并全网络撕裂卡白名单，仅用于「合成台 / 切石 / 锻造」等合成类样板的输入匹配（{@link com.lhy.ae2utility.card.NbtTearPatternMatchHelper#matchesCraftingPatternInput}）。
 * 处理样板改为按供应该配方的供应器单独校验，不使用此合并结果。
 */
public final class NbtTearNetworkHelper {

    private NbtTearNetworkHelper() {}

    /**
     * @return merged {@link NbtTearFilter} from all active tear cards in the network,
     *         or {@code null} if no tear cards are present.
     */
    public static NbtTearFilter computeGlobalFilter(CraftingService cs) {
        NetworkCraftingProviders providers = ((CraftingServiceAccessor) (Object) cs).ae2utility$getCraftingProviders();

        boolean anyEmptyWhitelist = false;
        List<ResourceLocation> allIds = new ArrayList<>();

        for (AEKey key : providers.getCraftableKeys()) {
            for (IPatternDetails pattern : providers.getCraftingFor(key)) {
                for (ICraftingProvider provider : providers.getMediums(pattern)) {
                    if (!(provider instanceof NbtTearLogicAccess access)) {
                        NbtTearCardDebug.logFuzzyCraftSearch("global_filter_provider_scan", key, provider, false, "provider_not_access");
                        continue;
                    }

                    ItemStack card = access.ae2utility$getEffectiveTearCardStack();
                    if (card.isEmpty() || !(card.getItem() instanceof NbtTearCardItem)) {
                        NbtTearCardDebug.logFuzzyCraftSearch("global_filter_provider_scan", key, provider, false, "no_tear_card");
                        continue;
                    }

                    NbtTearFilter f = card.getOrDefault(ModDataComponents.NBT_TEAR_FILTER, NbtTearFilter.DEFAULT);
                    NbtTearCardDebug.logFuzzyCraftSearch("global_filter_provider_scan", key, provider, true, "tear_card_found_" + f.itemIds().size());
                    if (f.itemIds().isEmpty()) {
                        anyEmptyWhitelist = true;
                    } else {
                        allIds.addAll(f.itemIds());
                    }
                }
            }
        }

        if (!anyEmptyWhitelist && allIds.isEmpty()) {
            return null;
        }
        if (anyEmptyWhitelist) {
            return NbtTearFilter.DEFAULT; // empty whitelist = all items
        }
        return new NbtTearFilter(allIds.stream().distinct().toList());
    }
}
