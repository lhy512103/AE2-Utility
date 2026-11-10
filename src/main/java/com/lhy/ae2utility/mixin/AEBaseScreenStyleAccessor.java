package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;

@Mixin(AEBaseScreen.class)
public interface AEBaseScreenStyleAccessor {
    @Accessor("style")
    ScreenStyle ae2utility$getStyle();
}
