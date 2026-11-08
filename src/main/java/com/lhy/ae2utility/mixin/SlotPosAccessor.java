package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.inventory.Slot;

@Mixin(Slot.class)
public interface SlotPosAccessor {
    @Accessor("x")
    int ae2utility$getX();

    @Accessor("y")
    int ae2utility$getY();

    @Accessor("x")
    void ae2utility$setX(int x);

    @Accessor("y")
    void ae2utility$setY(int y);
}
