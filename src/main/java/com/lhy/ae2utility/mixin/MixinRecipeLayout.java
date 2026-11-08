package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.lhy.ae2utility.client.jei.EncodePatternRecipeLayoutContext;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.gui.GuiGraphics;

@Mixin(targets = "mezz.jei.library.gui.recipes.RecipeLayout", remap = false, priority = 500)
public class MixinRecipeLayout {
    // JEI's optimized methods may reuse local 0 before RETURN. Capture this in
    // a wrapper instead of injecting an instance callback into the original tail.
    @WrapMethod(method = "tick")
    private void ae2utility$pushLayoutTick(Operation<Void> original) {
        EncodePatternRecipeLayoutContext.push((IRecipeLayoutDrawable<?>) (Object) this);
        try {
            original.call();
        } finally {
            EncodePatternRecipeLayoutContext.pop();
        }
    }

    @WrapMethod(method = "drawRecipe")
    private void ae2utility$pushLayoutDrawRecipe(GuiGraphics guiGraphics, int mouseX, int mouseY,
            Operation<Void> original) {
        EncodePatternRecipeLayoutContext.push((IRecipeLayoutDrawable<?>) (Object) this);
        try {
            original.call(guiGraphics, mouseX, mouseY);
        } finally {
            EncodePatternRecipeLayoutContext.pop();
        }
    }

    @WrapMethod(method = "drawOverlays")
    private void ae2utility$pushLayoutDrawOverlays(GuiGraphics guiGraphics, int mouseX, int mouseY,
            Operation<Void> original) {
        EncodePatternRecipeLayoutContext.push((IRecipeLayoutDrawable<?>) (Object) this);
        try {
            original.call(guiGraphics, mouseX, mouseY);
        } finally {
            EncodePatternRecipeLayoutContext.pop();
        }
    }
}
