package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.lhy.ae2utility.card.NbtTearPatternMatchHelper;

@Mixin(targets = "appeng/crafting/pattern/AECraftingPattern$Input", remap = false)
public class MixinAECraftingPatternInput {

    @Redirect(method = "isValid",
            at = @At(value = "INVOKE", target = "Lappeng/api/stacks/AEKey;matches(Lappeng/api/stacks/GenericStack;)Z"))
    private boolean ae2utility$redirectMatches(AEKey input, GenericStack templateStack) {
        return NbtTearPatternMatchHelper.matchesCraftingPatternInput(input, templateStack);
    }
}
