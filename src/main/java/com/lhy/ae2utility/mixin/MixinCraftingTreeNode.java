package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

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

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "getValidItemTemplates")
    private Iterable<InputTemplate> ae2utility$scopedTemplates(ICraftingInventory inv,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Iterable<InputTemplate>> original) {
        IPatternDetails details = parent != null && parentInput != null
                ? ((CraftingTreeProcessAccessor) (Object) parent).getDetails() : null;
        Iterable<InputTemplate> templates;
        try (var ignored = NbtTearPatternContext.enter(details)) {
            templates = original.call(inv);
        }
        // AE2 filters lazily. Scope each iterator operation, not the lifetime of the iterable.
        return com.lhy.ae2utility.card.NbtTearScopedIterable.wrap(templates,
                () -> NbtTearPatternContext.enter(details));
    }
}
