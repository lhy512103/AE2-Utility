package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;

import com.lhy.ae2utility.item.NbtTearCardItem;

/**
 * 在菜单校验层统一限制：一个样板供应器的全部升级槽里最多只允许存在一张撕裂卡。
 */
@Mixin(AEBaseMenu.class)
public abstract class MixinAEBaseMenu {

    @Inject(method = "isValidForSlot", at = @At("HEAD"), cancellable = true)
    private void ae2utility$enforceSingleTearAcrossUpgradeSlots(Slot slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!(stack.getItem() instanceof NbtTearCardItem)) {
            return;
        }
        if (!((Object) this instanceof PatternProviderMenu menu)) {
            return;
        }
        
        var slotSemantic = menu.getSlotSemantic(slot);
        if (slotSemantic != SlotSemantics.UPGRADE && slotSemantic != null) {
            return;
        }
        
        var upgradeSlots = menu.getSlots(SlotSemantics.UPGRADE);
        if (upgradeSlots == null) {
            return;
        }
        
        for (Slot upgradeSlot : upgradeSlots) {
            if (upgradeSlot == slot) {
                continue;
            }
            if (upgradeSlot.getItem().getItem() instanceof NbtTearCardItem) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
