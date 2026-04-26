package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ICraftingInventory;

import com.lhy.ae2utility.card.NbtTearPatternContext;

@Mixin(value = CraftingTreeNode.class, remap = false)
public class MixinCraftingTreeNode {

    @Shadow
    @Final
    private CraftingTreeProcess parent;

    @Shadow
    @Final
    private IPatternDetails.IInput parentInput;

    @Inject(method = "getValidItemTemplates", at = @At("HEAD"))
    private void ae2utility$pushPattern(ICraftingInventory inv, CallbackInfoReturnable<Iterable<InputTemplate>> cir) {
        // CraftingCpuHelper.getValidItemTemplates applies isValid lazily (Iterables.filter): callers iterate after
        // this method returns. Clearing pattern on RETURN would run before isValid and break per-provider tear checks.
        if (parent != null && parentInput != null) {
            NbtTearPatternContext.set(((CraftingTreeProcessAccessor) (Object) parent).getDetails());
        } else {
            NbtTearPatternContext.clear();
        }
    }
}
