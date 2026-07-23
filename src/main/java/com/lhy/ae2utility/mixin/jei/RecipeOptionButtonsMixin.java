package com.lhy.ae2utility.mixin.jei;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.lhy.ae2utility.jei.JeiRecipeOptionButton;

import mezz.jei.gui.elements.GuiIconToggleButton;
import mezz.jei.gui.recipes.RecipeOptionButtons;

@Mixin(value = RecipeOptionButtons.class, remap = false)
public abstract class RecipeOptionButtonsMixin {
    @Shadow
    @Final
    @Mutable
    private List<GuiIconToggleButton> buttons;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ae2utility$prependButtons(Runnable onValueChanged, CallbackInfo callbackInfo) {
        List<GuiIconToggleButton> extendedButtons = new ArrayList<>(buttons.size() + 4);
        extendedButtons.add(JeiRecipeOptionButton.create(JeiRecipeOptionButton.Kind.BATCH_PAGE));
        extendedButtons.add(JeiRecipeOptionButton.create(JeiRecipeOptionButton.Kind.BATCH_CATEGORY));
        extendedButtons.add(JeiRecipeOptionButton.create(JeiRecipeOptionButton.Kind.ITEM_SUBSTITUTION));
        extendedButtons.add(JeiRecipeOptionButton.create(JeiRecipeOptionButton.Kind.FLUID_SUBSTITUTION));
        extendedButtons.addAll(buttons);
        buttons = extendedButtons;
    }
}
