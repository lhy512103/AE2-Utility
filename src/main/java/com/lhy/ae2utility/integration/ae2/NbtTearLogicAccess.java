package com.lhy.ae2utility.integration.ae2;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * Implemented by mixin on {@link appeng.helpers.patternprovider.PatternProviderLogic}.
 */
public interface NbtTearLogicAccess {
    ItemStacksResourceHandler ae2utility$getTearHandler();

    PatternProviderFeatureCardCache ae2utility$getFeatureCardCache();

    default ItemStack ae2utility$getEffectiveTearCardStack() {
        return ae2utility$getFeatureCardCache().nbtTearCard();
    }
}
