package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.level.Level;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingCalculation;
import appeng.me.service.CraftingService;

import com.lhy.ae2utility.card.NbtTearPatternContext;
import com.lhy.ae2utility.card.NbtTearSimulationEnv;

@Mixin(value = CraftingCalculation.class, remap = false)
public class MixinCraftingCalculation {

    @Unique
    private IGrid ae2utility$grid;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ae2utility$captureGrid(Level level, IGrid grid, ICraftingSimulationRequester simRequester, GenericStack output,
            CalculationStrategy strategy, CallbackInfo ci) {
        this.ae2utility$grid = grid;
    }

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "run")
    private ICraftingPlan ae2utility$runScoped(
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<ICraftingPlan> original) {
        var service = ae2utility$grid == null ? null : ae2utility$grid.getCraftingService();
        try (var environment = NbtTearSimulationEnv.enter(service instanceof CraftingService cs ? cs : null);
                var pattern = NbtTearPatternContext.enter(null);
                var execution = com.lhy.ae2utility.card.NbtTearCardThreadLocal.enter(null);
                var crafting = com.lhy.ae2utility.card.NbtTearCraftingContext.enter(null)) {
            return original.call();
        }
    }
}
