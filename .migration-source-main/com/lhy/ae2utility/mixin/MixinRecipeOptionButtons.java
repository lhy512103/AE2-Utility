package com.lhy.ae2utility.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.lhy.ae2utility.jei.JeiPatternSubstitutionUi;

import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.recipes.RecipeOptionButtons;

@Mixin(value = RecipeOptionButtons.class, remap = false)
public abstract class MixinRecipeOptionButtons {
    // Optimized JEI releases reuse constructor local 0 for Textures. An instance
    // TAIL injector would load that value as `this` and produce a VerifyError.
    // Transform the list with a static handler before JEI assigns its final field.
    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE",
            target = "Ljava/util/List;of(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;"))
    private static List<IconButton> ae2utility$appendButtons(List<IconButton> original) {
        // These controllers update their visibility once the player is available.
        List<IconButton> buttons = new ArrayList<>(original);
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
        return buttons;
    }
}
