package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.CraftingSimulationState;

@Mixin(value = CraftingTreeProcess.class, remap = false)
public class MixinCraftingTreeProcess {

    @Shadow
    @Final
    private IPatternDetails details;

    @Shadow
    @Final
    private CraftingTreeNode parent;

    @Inject(method = "getOutputCount", at = @At("RETURN"), cancellable = true)
    private void ae2utility$fuzzyOutputCount(AEKey what, CallbackInfoReturnable<Long> cir) {
        if (cir.getReturnValue() == 0L && this.details != null && what != null) {
            long tot = 0;
            for (var is : this.details.getOutputs()) {
                if (is != null && is.what() != null && what.dropSecondary().equals(is.what().dropSecondary())) {
                    tot += is.amount();
                }
            }
            if (tot > 0) {
                cir.setReturnValue(tot);
            }
        }
    }

    @Redirect(
            method = "request",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/crafting/inv/CraftingSimulationState;insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)V"))
    private void ae2utility$redirectInsertFuzzyOutputs(CraftingSimulationState inv, AEKey what, long amount,
            Actionable mode) {
        if (this.parent != null) {
            AEKey parentWhat = ((CraftingTreeNodeAccessor) this.parent).ae2utility$getWhat();
            if (parentWhat != null && what != null && !what.equals(parentWhat)
                    && what.dropSecondary().equals(parentWhat.dropSecondary())) {
                what = parentWhat;
            }
        }
        inv.insert(what, amount, mode);
    }
}
