package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.Level;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingCalculation;
import appeng.me.service.CraftingService;

import com.lhy.ae2utility.card.NbtTearCraftingContext;
import com.lhy.ae2utility.card.NbtTearNetworkHelper;
import com.lhy.ae2utility.card.NbtTearPatternContext;
import com.lhy.ae2utility.card.NbtTearSimulationEnv;
import com.lhy.ae2utility.debug.NbtTearCardDebug;

@Mixin(value = CraftingCalculation.class, remap = false)
public class MixinCraftingCalculation {

    @Unique
    private IGrid ae2utility$grid;

    @Unique
    private ICraftingSimulationRequester ae2utility$simRequester;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ae2utility$captureGrid(Level level, IGrid grid, ICraftingSimulationRequester simRequester, GenericStack output,
            CalculationStrategy strategy, CallbackInfo ci) {
        this.ae2utility$grid = grid;
        this.ae2utility$simRequester = simRequester;
    }

    @Inject(method = "run", at = @At("HEAD"))
    private void ae2utility$pushGlobalFilter(CallbackInfoReturnable<ICraftingPlan> cir) {
        if (ae2utility$grid == null) return;
        var svc = ae2utility$grid.getCraftingService();
        if (svc instanceof CraftingService cs) {
            NbtTearSimulationEnv.beginCalculation(cs, ae2utility$simRequester);
            var filter = NbtTearNetworkHelper.computeGlobalFilter(cs);
            NbtTearCraftingContext.set(filter);
            NbtTearCardDebug.logGlobalFilter("calculation_run", filter);
        }
    }

    @Inject(method = "finish", at = @At("HEAD"))
    private void ae2utility$clearGlobalFilter(CallbackInfo ci) {
        NbtTearSimulationEnv.clear();
        NbtTearCraftingContext.clear();
        NbtTearPatternContext.clear();
    }
}
