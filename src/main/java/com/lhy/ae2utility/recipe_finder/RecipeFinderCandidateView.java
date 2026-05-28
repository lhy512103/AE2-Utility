package com.lhy.ae2utility.recipe_finder;

import java.util.List;
import java.util.Set;

import net.minecraft.world.item.ItemStack;

import com.lhy.ae2utility.network.EncodePatternPacket;

public record RecipeFinderCandidateView(
        String identityKey,
        ItemStack previewStack,
        String displayName,
        String sourceModId,
        String sourceModName,
        String machineKey,
        String machineLabel,
        String recipeId,
        Set<String> outputItemIds,
        Set<String> inputItemIds,
        Set<String> involvedModIds,
        Set<String> inputFeatureKeys,
        Set<String> outputFeatureKeys,
        List<String> inputDisplayNames,
        List<String> extraOutputDisplayNames,
        boolean encodable,
        EncodePatternPacket encodePacket) {
}
