package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.lhy.ae2utility.jei.JeiPatternSubstitutionUi;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import mezz.jei.common.util.ImmutableRect2i;

/**
 * 在 JEI 配方页整页左上角绘制一次样板替换开关（与 {@code RecipesGui#getRecipeLayoutsArea()} 对齐），
 * 并在 {@link mezz.jei.gui.recipes.RecipesGui#mouseClicked} 中处理命中（坐标与 Gui 流水线一致）。
 */
@Mixin(targets = "mezz.jei.gui.recipes.RecipesGui", remap = false)
public class MixinRecipesGui {
    private static final int BORDER_PADDING = 6;
    private static final int NAV_BAR_PADDING = 2;

    @Shadow
    private ImmutableRect2i area;

    @Shadow
    private int headerHeight;

    private ImmutableRect2i ae2utility$recipeLayoutsArea() {
        return new ImmutableRect2i(
                area.getX() + BORDER_PADDING,
                area.getY() + headerHeight + NAV_BAR_PADDING,
                area.getWidth() - (2 * BORDER_PADDING),
                area.getHeight() - (headerHeight + BORDER_PADDING + NAV_BAR_PADDING));
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ae2utility$substitutionToolbarClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) {
            return;
        }
        ImmutableRect2i layoutsArea = ae2utility$recipeLayoutsArea();
        JeiPatternSubstitutionUi.syncLayoutFromRecipeArea(layoutsArea);
        if (JeiPatternSubstitutionUi.handleClick(mouseX, mouseY)) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void ae2utility$renderPatternSubstitutionToggles(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        JeiPatternSubstitutionUi.render(ae2utility$recipeLayoutsArea(), guiGraphics, mouseX, mouseY);
    }
}
