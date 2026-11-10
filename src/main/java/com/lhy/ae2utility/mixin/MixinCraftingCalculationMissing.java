package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingCalculation;

import com.lhy.ae2utility.debug.NbtTearCardDebug;

@Mixin(value = CraftingCalculation.class, remap = false)
public class MixinCraftingCalculationMissing {

    @Inject(method = "addMissing", at = @At("HEAD"))
    private void ae2utility$logMissing(AEKey what, long amount, CallbackInfo ci) {
        NbtTearCardDebug.logMissingIngredient(what, amount);
    }
}
