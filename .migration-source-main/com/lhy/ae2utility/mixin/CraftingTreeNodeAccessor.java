package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingTreeNode;

@Mixin(value = CraftingTreeNode.class, remap = false)
public interface CraftingTreeNodeAccessor {
    @Accessor("what")
    AEKey ae2utility$getWhat();
}
