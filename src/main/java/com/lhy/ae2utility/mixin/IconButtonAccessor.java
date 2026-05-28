package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import mezz.jei.api.gui.buttons.IIconButtonController;

@Mixin(targets = "mezz.jei.gui.elements.IconButton", remap = false)
public interface IconButtonAccessor {

    @Accessor("controller")
    IIconButtonController ae2utility$controller();
}
