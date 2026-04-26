package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;

import com.lhy.ae2utility.card.NbtTearCardThreadLocal;
import com.lhy.ae2utility.card.NbtTearCraftingContext;
import com.lhy.ae2utility.card.NbtTearFilter;
import com.lhy.ae2utility.card.NbtTearNetworkHelper;
import com.lhy.ae2utility.card.NbtTearSimulationEnv;

@Mixin(value = CraftingCpuLogic.class, remap = false)
public class MixinCraftingCpuLogic {

    /**
     * Before the CPU dispatches pattern inputs, publish a global tear filter so that
     * {@link MixinAEProcessingPatternInput} can relax NBT checks during extractPatternInputs.
     */
    @Inject(method = "executeCrafting", at = @At("HEAD"))
    private void ae2utility$pushGlobalFilter(int maxPatterns, CraftingService craftingService, IEnergyService energyService, Level level,
            CallbackInfoReturnable<Integer> cir) {
        NbtTearSimulationEnv.beginCpuExtract(craftingService);
        NbtTearCraftingContext.set(NbtTearNetworkHelper.computeGlobalFilter(craftingService));
    }

    @Inject(method = "executeCrafting", at = @At("RETURN"))
    private void ae2utility$popGlobalFilter(CallbackInfoReturnable<Integer> cir) {
        NbtTearSimulationEnv.clear();
        NbtTearCraftingContext.clear();
    }

    // -----------------------------------------------------------------------
    // Return-inventory matching – uses NbtTearCardThreadLocal set by
    // MixinPatternProviderLogic.doWork so we can accept products with
    // different NBT than what was requested.
    // -----------------------------------------------------------------------

    @Redirect(method = "insert", at = @At(value = "INVOKE", target = "Lappeng/crafting/inv/ListCraftingInventory;extract(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J"))
    private long ae2utility$redirectExtract(ListCraftingInventory instance, AEKey what, long amount, Actionable mode) {
        NbtTearFilter filter = NbtTearCardThreadLocal.get();
        if (filter != null) {
            for (var entry : instance.list) {
                if (NbtTearFilter.matchesUnlockExpected(entry.getKey(), what, filter)) {
                    return instance.extract(entry.getKey(), amount, mode);
                }
            }
        }
        return instance.extract(what, amount, mode);
    }

    @Redirect(method = "insert", at = @At(value = "INVOKE", target = "Lappeng/api/stacks/AEKey;matches(Lappeng/api/stacks/GenericStack;)Z"))
    private boolean ae2utility$redirectMatches(AEKey what, GenericStack finalOutput) {
        NbtTearFilter filter = NbtTearCardThreadLocal.get();
        if (filter != null && finalOutput != null) {
            if (NbtTearFilter.matchesUnlockExpected(finalOutput.what(), what, filter)) {
                return true;
            }
        }
        return what != null && what.matches(finalOutput);
    }
}
