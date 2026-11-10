package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ICraftingInventory;
import appeng.api.stacks.KeyCounter;

import com.lhy.ae2utility.card.NbtTearPatternContext;

@Mixin(value = CraftingCpuHelper.class, remap = false)
public class MixinCraftingCpuHelper {

    @Inject(method = "extractPatternInputs", at = @At("HEAD"))
    private static void ae2utility$pushPattern(IPatternDetails details, ICraftingInventory sourceInv, Level level,
            KeyCounter expectedOutputs, KeyCounter expectedContainerItems, CallbackInfoReturnable<KeyCounter[]> cir) {
        NbtTearPatternContext.set(details);
    }

    @Inject(method = "extractPatternInputs", at = @At("RETURN"))
    private static void ae2utility$popPattern(CallbackInfoReturnable<KeyCounter[]> cir) {
        NbtTearPatternContext.clear();
    }
}
