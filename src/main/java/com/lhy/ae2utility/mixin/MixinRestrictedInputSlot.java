package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.menu.slot.RestrictedInputSlot;
import net.minecraft.world.item.ItemStack;

import com.lhy.ae2utility.compat.PatternProviderMenuCompat;
import com.lhy.ae2utility.item.NbtTearCardItem;
import com.lhy.ae2utility.item.RedstoneSignalCardItem;

@Mixin(RestrictedInputSlot.class)
public abstract class MixinRestrictedInputSlot {
    @Shadow
    @Final
    private RestrictedInputSlot.PlacableItemType which;

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void ae2utility$allowTearCardInPatternProviderUpgrades(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (this.which != RestrictedInputSlot.PlacableItemType.UPGRADES) {
            return;
        }
        if (!(stack.getItem() instanceof NbtTearCardItem) && !(stack.getItem() instanceof RedstoneSignalCardItem)) {
            return;
        }
        RestrictedInputSlot self = (RestrictedInputSlot) (Object) this;
        var menu = ((AppEngSlotMenuAccessor) self).ae2utility$getMenu();
        if (!PatternProviderMenuCompat.isSupportedPatternProviderMenu(menu)) {
            return;
        }
        cir.setReturnValue(menu.isValidForSlot(self, stack));
    }
}
