package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.api.crafting.IPatternDetails;
import appeng.crafting.CraftingTreeProcess;

@Mixin(value = CraftingTreeProcess.class, remap = false)
public interface CraftingTreeProcessAccessor {

    @Accessor("details")
    IPatternDetails getDetails();
}
