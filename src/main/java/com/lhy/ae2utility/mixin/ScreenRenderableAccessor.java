package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;

@Mixin(Screen.class)
public interface ScreenRenderableAccessor {
    @Invoker("addRenderableOnly")
    <T extends Renderable> T ae2utility$addRenderableOnly(T widget);
}
