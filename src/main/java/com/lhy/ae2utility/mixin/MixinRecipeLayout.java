package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import com.lhy.ae2utility.client.jei.EncodePatternRecipeLayoutContext;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Publishes the current recipe layout to {@link EncodePatternRecipeLayoutContext} while JEI ticks or draws it.
 *
 * <p>Uses {@link WrapMethod} rather than HEAD/RETURN {@code @Inject}s. The shipped JEI bytecode reuses local slot 0
 * (normally {@code this}) for other values inside these methods, so a RETURN injection that loads {@code this}
 * fails bytecode verification and disables JEI's GUI. Wrapping also guarantees the pop runs when JEI throws.</p>
 */
@Mixin(targets = "mezz.jei.library.gui.recipes.RecipeLayout", remap = false, priority = 500)
public class MixinRecipeLayout {

    private IRecipeLayoutDrawable<?> ae2utility$selfLayout() {
        return (IRecipeLayoutDrawable<?>) (Object) this;
    }

    @WrapMethod(method = "tick")
    private void ae2utility$withLayoutTick(Operation<Void> original) {
        EncodePatternRecipeLayoutContext.push(ae2utility$selfLayout());
        try {
            original.call();
        } finally {
            EncodePatternRecipeLayoutContext.pop();
        }
    }

    @WrapMethod(method = "drawRecipe")
    private void ae2utility$withLayoutDrawRecipe(GuiGraphics guiGraphics, int mouseX, int mouseY,
            Operation<Void> original) {
        EncodePatternRecipeLayoutContext.push(ae2utility$selfLayout());
        try {
            original.call(guiGraphics, mouseX, mouseY);
        } finally {
            EncodePatternRecipeLayoutContext.pop();
        }
    }

    @WrapMethod(method = "drawOverlays")
    private void ae2utility$withLayoutDrawOverlays(GuiGraphics guiGraphics, int mouseX, int mouseY,
            Operation<Void> original) {
        EncodePatternRecipeLayoutContext.push(ae2utility$selfLayout());
        try {
            original.call(guiGraphics, mouseX, mouseY);
        } finally {
            EncodePatternRecipeLayoutContext.pop();
        }
    }
}
