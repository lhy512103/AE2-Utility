package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.lhy.ae2utility.card.NbtTearPatternMatchHelper;

/**
 * Relaxes the strict NBT check inside {@code AEProcessingPattern.Input.isValid}.
 * <p>
 * AE2 calls {@code input.matches(template[0])} which requires an exact NBT match.
 * 仅当供应该配方的样板供应器上装有适用的 NBT 撕裂卡时才放宽（见 {@link NbtTearPatternMatchHelper#matchesProcessingPatternInput}）。
 */
@Mixin(targets = "appeng/crafting/pattern/AEProcessingPattern$Input", remap = false)
public class MixinAEProcessingPatternInput {

    @Redirect(method = "isValid",
            at = @At(value = "INVOKE", target = "Lappeng/api/stacks/AEKey;matches(Lappeng/api/stacks/GenericStack;)Z"))
    private boolean ae2utility$redirectMatches(AEKey input, GenericStack templateStack) {
        return NbtTearPatternMatchHelper.matchesProcessingPatternInput(input, templateStack);
    }
}
