package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.menu.AEBaseMenu;
import appeng.menu.slot.AppEngSlot;

@Mixin(AppEngSlot.class)
public interface AppEngSlotMenuAccessor {
    @Accessor("menu")
    AEBaseMenu ae2utility$getMenu();
}
