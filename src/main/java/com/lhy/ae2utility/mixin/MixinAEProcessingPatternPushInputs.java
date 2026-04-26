package com.lhy.ae2utility.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.lhy.ae2utility.card.NbtTearExecutionHelper;

@Mixin(targets = "appeng/crafting/pattern/AEProcessingPattern", remap = false)
public class MixinAEProcessingPatternPushInputs {

    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private List<GenericStack> sparseInputs;

    @Inject(method = "pushInputsToExternalInventory", at = @At("HEAD"), cancellable = true)
    private void ae2utility$pushInputsWithTear(KeyCounter[] inputHolder, IPatternDetails.PatternInputSink inputSink, CallbackInfo ci) {
        if (NbtTearExecutionHelper.pushSparseInputsWithTear(inputHolder, inputSink, sparseInputs)) {
            ci.cancel();
        }
    }
}
