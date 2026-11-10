package com.lhy.ae2utility.mixin;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import com.lhy.ae2utility.card.NbtTearCardThreadLocal;
import com.lhy.ae2utility.card.NbtTearFilter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** NeoEco 26.2.15 moved the actual waiting-output consumption out of its CPU facade. */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingOutputDelivery", remap = false)
public class MixinNeoEcoOutputDelivery {
    @Redirect(method = "insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Ljava/lang/Long;Ljava/lang/Boolean;)J",
            at = @At(value = "INVOKE", target = "Lappeng/crafting/inv/ListCraftingInventory;extract(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J"))
    private long ae2utility$extractReturnedOutput(ListCraftingInventory inventory, AEKey what, long amount, Actionable mode) {
        var filter = NbtTearCardThreadLocal.get();
        if (filter != null) {
            for (var entry : inventory.list) {
                if (NbtTearFilter.matchesUnlockExpected(entry.getKey(), what, filter)) {
                    return inventory.extract(entry.getKey(), amount, mode);
                }
            }
        }
        return inventory.extract(what, amount, mode);
    }

    @Redirect(method = "insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Ljava/lang/Long;Ljava/lang/Boolean;)J",
            at = @At(value = "INVOKE", target = "Lappeng/api/stacks/AEKey;matches(Lappeng/api/stacks/GenericStack;)Z"))
    private boolean ae2utility$matchReturnedOutput(AEKey what, GenericStack expected) {
        var filter = NbtTearCardThreadLocal.get();
        return filter != null && expected != null && NbtTearFilter.matchesUnlockExpected(expected.what(), what, filter)
                || what != null && what.matches(expected);
    }
}
