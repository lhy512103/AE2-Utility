package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import mezz.jei.common.util.ImmutableRect2i;
import net.minecraft.client.gui.GuiGraphics;

import com.lhy.ae2utility.jei.JeiPatternSubstitutionUi;

/**
 * 在 JEI 配方页整页左上角绘制一次样板替换开关（与 {@code RecipesGui#getRecipeLayoutsArea()} 对齐）。
 */
@Mixin(targets = "mezz.jei.gui.recipes.RecipesGui", remap = false)
public class MixinRecipesGui {
    private static final int BORDER_PADDING = 6;
    private static final int NAV_BAR_PADDING = 2;

    @Shadow
    private ImmutableRect2i area;

    @Shadow
    private int headerHeight;

    @Inject(method = "render", at = @At("TAIL"))
    private void ae2utility$renderPatternSubstitutionToggles(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        ImmutableRect2i recipeLayoutsArea = new ImmutableRect2i(
                area.getX() + BORDER_PADDING,
                area.getY() + headerHeight + NAV_BAR_PADDING,
                area.getWidth() - (2 * BORDER_PADDING),
                area.getHeight() - (headerHeight + BORDER_PADDING + NAV_BAR_PADDING)
        );
        JeiPatternSubstitutionUi.render(recipeLayoutsArea, guiGraphics, mouseX, mouseY);
    }
}
