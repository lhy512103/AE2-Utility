package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.lhy.ae2utility.client.jei.EncodePatternRecipeLayoutContext;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.gui.GuiGraphics;

@Mixin(targets = "mezz.jei.library.gui.recipes.RecipeLayout", remap = false, priority = 500)
public class MixinRecipeLayout {

    private static IRecipeLayoutDrawable<?> ae2utility$selfLayout(Object self) {
        return (IRecipeLayoutDrawable<?>) self;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void ae2utility$pushLayoutTick(CallbackInfo ci) {
        EncodePatternRecipeLayoutContext.push(ae2utility$selfLayout(this));
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void ae2utility$popLayoutTick(CallbackInfo ci) {
        EncodePatternRecipeLayoutContext.pop();
    }

    @Inject(method = "drawRecipe", at = @At("HEAD"))
    private void ae2utility$pushLayoutDrawRecipe(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        EncodePatternRecipeLayoutContext.push(ae2utility$selfLayout(this));
    }

    @Inject(method = "drawRecipe", at = @At("RETURN"))
    private void ae2utility$popLayoutDrawRecipe(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        EncodePatternRecipeLayoutContext.pop();
    }

    @Inject(method = "drawOverlays", at = @At("HEAD"))
    private void ae2utility$pushLayoutDrawOverlays(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        EncodePatternRecipeLayoutContext.push(ae2utility$selfLayout(this));
    }

    @Inject(method = "drawOverlays", at = @At("RETURN"))
    private void ae2utility$popLayoutDrawOverlays(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        EncodePatternRecipeLayoutContext.pop();
    }

}
