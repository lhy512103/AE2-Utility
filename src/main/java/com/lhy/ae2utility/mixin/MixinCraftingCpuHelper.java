package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.world.level.Level;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ICraftingInventory;
import appeng.api.stacks.KeyCounter;

import com.lhy.ae2utility.card.NbtTearPatternContext;

@Mixin(value = CraftingCpuHelper.class, remap = false)
public class MixinCraftingCpuHelper {

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "extractPatternInputs")
    private static KeyCounter[] ae2utility$extractScoped(IPatternDetails details, ICraftingInventory sourceInv, Level level,
            KeyCounter expectedOutputs, KeyCounter expectedContainerItems,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<KeyCounter[]> original) {
        try (var ignored = NbtTearPatternContext.enter(details)) {
            return original.call(details, sourceInv, level, expectedOutputs, expectedContainerItems);
        }
    }
}
