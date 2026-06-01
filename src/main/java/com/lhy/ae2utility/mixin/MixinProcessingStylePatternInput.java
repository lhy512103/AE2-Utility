package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.lhy.ae2utility.card.NbtTearPatternMatchHelper;

/**
 * 统一放宽 AE 与 Advanced AE 处理样板 Input 在 {@code isValid} 中对模板 NBT 的严格匹配。
 */
@Mixin(targets = {
        "appeng/crafting/pattern/AEProcessingPattern$Input",
        "net/pedroksl/advanced_ae/common/patterns/AdvProcessingPattern$Input"
}, remap = false)
public class MixinProcessingStylePatternInput {

    @Redirect(method = "isValid",
            at = @At(value = "INVOKE", target = "Lappeng/api/stacks/AEKey;matches(Lappeng/api/stacks/GenericStack;)Z"))
    private boolean ae2utility$redirectMatches(AEKey input, GenericStack templateStack) {
        return NbtTearPatternMatchHelper.matchesProcessingPatternInput(input, templateStack);
    }
}
