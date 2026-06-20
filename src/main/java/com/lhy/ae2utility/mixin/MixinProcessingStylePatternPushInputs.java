package com.lhy.ae2utility.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.lhy.ae2utility.card.NbtTearExecutionHelper;

/**
 * 统一为 AE/Advanced AE/AE2CS 处理样板的 {@code pushInputsToExternalInventory} 增加 NBT 撕裂卡兼容路径。
 */
@Mixin(targets = {
        "appeng/crafting/pattern/AEProcessingPattern",
        "net/pedroksl/advanced_ae/common/patterns/AdvProcessingPattern",
        "io.github.lounode.ae2cs.common.me.crafting.ResonatingPatternDetails"
}, remap = false)
public class MixinProcessingStylePatternPushInputs {

    @Shadow(remap = false)
    @Final
    private List<GenericStack> sparseInputs;

    @Inject(method = "pushInputsToExternalInventory", at = @At("HEAD"), cancellable = true)
    private void ae2utility$pushInputsWithTear(KeyCounter[] inputHolder, IPatternDetails.PatternInputSink inputSink,
            CallbackInfo ci) {
        if (NbtTearExecutionHelper.pushSparseInputsWithTear(inputHolder, inputSink, sparseInputs)) {
            ci.cancel();
        }
    }
}
