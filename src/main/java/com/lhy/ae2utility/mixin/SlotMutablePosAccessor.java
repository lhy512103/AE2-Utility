package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.inventory.Slot;

/**
 * 1.21 中 {@link Slot#x}/{@link Slot#y} 为 final，布局需在构造之后调整时通过 Mixin Accessor + {@link Mutable} 写入。
 */
@Mixin(Slot.class)
public interface SlotMutablePosAccessor {
    @Accessor("x")
    @Mutable
    void ae2utility$setX(int x);

    @Accessor("y")
    @Mutable
    void ae2utility$setY(int y);
}
