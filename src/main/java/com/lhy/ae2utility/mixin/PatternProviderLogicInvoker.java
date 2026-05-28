package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

@Mixin(value = PatternProviderLogic.class, remap = false)
public interface PatternProviderLogicInvoker {
    @Accessor("host")
    PatternProviderLogicHost ae2utility$getHost();

    @Invoker("onPushPatternSuccess")
    void ae2utility$onPushPatternSuccess(IPatternDetails pattern);

    @Invoker("onStackReturnedToNetwork")
    void ae2utility$onStackReturnedToNetwork(GenericStack stack);
}
