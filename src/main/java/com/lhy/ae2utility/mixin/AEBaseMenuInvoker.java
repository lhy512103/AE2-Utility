package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.inventory.Slot;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;

/**
 * AE2 19.2：{@code addSlot(Slot)} 在 {@link AEBaseMenu} 上为 protected，{@link appeng.menu.implementations.PatternProviderMenu} 未再声明，故用 Invoker 挂到定义类上。
 */
@Mixin(AEBaseMenu.class)
public interface AEBaseMenuInvoker {
    @Invoker("addSlot")
    Slot ae2utility$addSlot(Slot slot);

    @Invoker("addSlot")
    Slot ae2utility$addSlot(Slot slot, SlotSemantic semantic);
}
