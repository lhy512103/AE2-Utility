package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.helpers.patternprovider.PatternProviderReturnInventory;

/** Full return buffer, rather than the current nine-slot UI page, on AE2LT's new layout. */
@Pseudo
@Mixin(targets = "com.moakiee.ae2lt.logic.provider.OverloadedPatternProviderLogic", remap = false)
public interface OverloadedPatternProviderLogicAccessor {
    @Invoker("getInternalReturnInv")
    PatternProviderReturnInventory ae2utility$getInternalReturnInv();
}
