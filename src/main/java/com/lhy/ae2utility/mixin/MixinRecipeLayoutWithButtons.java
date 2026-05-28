package com.lhy.ae2utility.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.elements.IconButton;

import com.lhy.ae2utility.jei.EncodePatternButtonController;

@Mixin(targets = "mezz.jei.gui.recipes.RecipeLayoutWithButtons", remap = false)
public class MixinRecipeLayoutWithButtons {

    @Shadow
    @Final
    private List<IconButton> buttons;

    @Inject(method = "updateBounds", at = @At("RETURN"))
    private void ae2utility$syncEncodeButtonBounds(int recipeXOffset, int recipeYOffset, CallbackInfo ci) {
        for (IconButton button : buttons) {
            IIconButtonController c = ((IconButtonAccessor) (Object) button).ae2utility$controller();
            if (c instanceof EncodePatternButtonController enc) {
                enc.syncJeiEncodeRowOnVisiblePage(button.isVisible());
                if (button.isVisible()) {
                    enc.syncEncodeButtonScreenBounds(button.getArea());
                } else {
                    enc.syncEncodeButtonScreenBounds(ImmutableRect2i.EMPTY);
                }
                return;
            }
        }
    }
}
