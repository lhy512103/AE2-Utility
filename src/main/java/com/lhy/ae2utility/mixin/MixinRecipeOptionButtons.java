package com.lhy.ae2utility.mixin;

import java.util.List;
import java.util.ArrayList;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.lhy.ae2utility.jei.JeiPatternSubstitutionUi;

import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.recipes.RecipeOptionButtons;

@Mixin(value = RecipeOptionButtons.class, remap = false)
public abstract class MixinRecipeOptionButtons {
    @Shadow
    @Final
    @Mutable
    private List<IconButton> buttons;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ae2utility$appendButtons(Runnable onValueChanged, CallbackInfo ci) {
        // JEI may construct this container before the local player is available.
        // Register the buttons unconditionally; their controllers update visibility
        // as soon as the player/context becomes available.
        buttons = new ArrayList<>(buttons);
        buttons.add(new IconButton(new JeiPatternSubstitutionUi.RecipeOptionButtonController(
                JeiPatternSubstitutionUi.RecipeOptionButton.ITEM_SUBSTITUTION)));
        buttons.add(new IconButton(new JeiPatternSubstitutionUi.RecipeOptionButtonController(
                JeiPatternSubstitutionUi.RecipeOptionButton.FLUID_SUBSTITUTION)));
        buttons.add(new IconButton(new JeiPatternSubstitutionUi.RecipeOptionButtonController(
                JeiPatternSubstitutionUi.RecipeOptionButton.BATCH_PAGE)));
        if (com.lhy.ae2utility.client.Ae2UtilityClientConfig.showJeiBatchEncodeFullCategoryButton()) {
            buttons.add(new IconButton(new JeiPatternSubstitutionUi.RecipeOptionButtonController(
                    JeiPatternSubstitutionUi.RecipeOptionButton.BATCH_CATEGORY)));
        }
    }
}
