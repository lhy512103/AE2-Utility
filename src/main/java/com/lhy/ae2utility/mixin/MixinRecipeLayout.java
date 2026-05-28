package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.lhy.ae2utility.client.jei.EncodePatternRecipeLayoutContext;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;

@Mixin(targets = "mezz.jei.library.gui.recipes.RecipeLayout", remap = false, priority = 500)
public class MixinRecipeLayout {

    @SuppressWarnings("unchecked")
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

    @Inject(method = "getSideButtonArea", at = @At("RETURN"), cancellable = true)
    private void onGetSideButtonArea(int buttonIndex, CallbackInfoReturnable<Rect2i> cir) {
        if (this instanceof IRecipeLayoutDrawable<?> layout) {
            int height = layout.getRect().getHeight();
            // 如果是比较矮的配方（比如锻造台），而且是第3个按钮（index 2，即我们的向上箭头），
            // 将其放置在第1个按钮（index 0）的右侧。
            if (height < 44 && buttonIndex >= 2) {
                Rect2i original = cir.getReturnValue();
                int buttonWidth = original.getWidth();
                int buttonHeight = original.getHeight();
                int spacing = 2;

                // 恢复出 button 0 的 Y 坐标（最底部的按钮）
                int baseY = original.getY() + buttonIndex * (buttonHeight + spacing);

                // 将按钮分成多列，每列 2 个按钮
                int col = buttonIndex / 2;
                int row = buttonIndex % 2;

                int newX = original.getX() + col * (buttonWidth + spacing);
                int newY = baseY - row * (buttonHeight + spacing);

                cir.setReturnValue(new Rect2i(newX, newY, buttonWidth, buttonHeight));
            }
        }
    }
}
