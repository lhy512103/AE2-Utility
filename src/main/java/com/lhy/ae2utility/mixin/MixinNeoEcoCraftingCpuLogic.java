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
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;

import com.lhy.ae2utility.card.NbtTearCardThreadLocal;
import com.lhy.ae2utility.card.NbtTearFilter;
import com.lhy.ae2utility.card.NbtTearSimulationEnv;

/**
 * 统一为 AE2/AdvancedAE/NeoEcoAE 的 CraftingCPULogic 加入 NBT 撕裂卡的 simulate 环境与 return-inventory 匹配放宽。
 */
@org.spongepowered.asm.mixin.Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic", remap = false)
public class MixinNeoEcoCraftingCpuLogic {

    @Inject(method = "tickCraftingLogic", at = @At("HEAD"))
    private void ae2utility$pushGlobalFilter(IEnergyService energyService, CraftingService craftingService,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        NbtTearSimulationEnv.beginCpuExtract(craftingService);
    }

    @Inject(method = "tickCraftingLogic", at = @At("RETURN"))
    private void ae2utility$popGlobalFilter(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        NbtTearSimulationEnv.clear();
    }

    @Redirect(method = "insert", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/inv/ListCraftingInventory;extract(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J"))
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

    @Redirect(method = "insert", at = @At(value = "INVOKE",
            target = "Lappeng/api/stacks/AEKey;matches(Lappeng/api/stacks/GenericStack;)Z"))
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
