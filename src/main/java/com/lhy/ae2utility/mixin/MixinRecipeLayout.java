package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.renderer.Rect2i;

@Mixin(targets = "mezz.jei.library.gui.recipes.RecipeLayout", remap = false)
public class MixinRecipeLayout {

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
