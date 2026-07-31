package com.lhy.ae2utility.integration.ae2;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import appeng.helpers.patternprovider.PatternProviderLogic;

/**
 * Implemented by mixin on {@link appeng.helpers.patternprovider.PatternProviderLogic}.
 */
public interface NbtTearLogicAccess {
    ItemStackHandler ae2utility$getTearHandler();

    PatternProviderFeatureCardCache ae2utility$getFeatureCardCache();

    default ItemStack ae2utility$getEffectiveTearCardStack() {
        return ae2utility$getFeatureCardCache().nbtTearCard();
    }
}
