package com.lhy.ae2utility.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.lhy.ae2utility.client.Ae2UtilityClientConfig;
import com.lhy.ae2utility.jei.JeiPatternSubstitutionUi;

import mezz.jei.common.input.IInputTarget;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.recipes.RecipeOptionButtons;

/**
 * Appends the AE2 Utility buttons to JEI's recipe option bar.
 *
 * <p>Do not inject into the constructor. The shipped JEI bytecode reuses local slot 0 (normally
 * {@code this}) for a {@code Textures} value partway through {@code <init>}, so any constructor injection
 * that loads {@code this} fails verification and takes down JEI's whole GUI plugin. Every method that reads
 * {@code buttons} instead appends the buttons once at HEAD, where slot 0 is always {@code this}.</p>
 */
@Mixin(value = RecipeOptionButtons.class, remap = false)
public abstract class MixinRecipeOptionButtons {
    @Shadow
    @Final
    @Mutable
    private List<IconButton> buttons;

    @Unique
    private boolean ae2utility$buttonsAppended;

    @Unique
    private void ae2utility$appendButtons() {
        if (ae2utility$buttonsAppended) {
            return;
        }
        ae2utility$buttonsAppended = true;
        // JEI may build this bar before the local player exists. Register the buttons unconditionally;
        // their controllers update visibility once the player/context becomes available.
        List<IconButton> extended = new ArrayList<>(buttons);
        extended.add(new IconButton(new JeiPatternSubstitutionUi.RecipeOptionButtonController(
                JeiPatternSubstitutionUi.RecipeOptionButton.ITEM_SUBSTITUTION)));
        extended.add(new IconButton(new JeiPatternSubstitutionUi.RecipeOptionButtonController(
                JeiPatternSubstitutionUi.RecipeOptionButton.FLUID_SUBSTITUTION)));
        extended.add(new IconButton(new JeiPatternSubstitutionUi.RecipeOptionButtonController(
                JeiPatternSubstitutionUi.RecipeOptionButton.BATCH_PAGE)));
        if (Ae2UtilityClientConfig.showJeiBatchEncodeFullCategoryButton()) {
            extended.add(new IconButton(new JeiPatternSubstitutionUi.RecipeOptionButtonController(
                    JeiPatternSubstitutionUi.RecipeOptionButton.BATCH_CATEGORY)));
        }
        buttons = extended;
    }

    @Inject(method = "createInputHandler", at = @At("HEAD"))
    private void ae2utility$beforeCreateInputHandler(CallbackInfoReturnable<IInputTarget> cir) {
        ae2utility$appendButtons();
    }

    @Inject(method = "updateLayout", at = @At("HEAD"))
    private void ae2utility$beforeUpdateLayout(CallbackInfo ci) {
        ae2utility$appendButtons();
    }

    @Inject(method = "getWidth", at = @At("HEAD"))
    private void ae2utility$beforeGetWidth(CallbackInfoReturnable<Integer> cir) {
        ae2utility$appendButtons();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void ae2utility$beforeTick(CallbackInfo ci) {
        ae2utility$appendButtons();
    }

    @Inject(method = "draw", at = @At("HEAD"))
    private void ae2utility$beforeDraw(CallbackInfo ci) {
        ae2utility$appendButtons();
    }

    @Inject(method = "drawTooltips", at = @At("HEAD"))
    private void ae2utility$beforeDrawTooltips(CallbackInfo ci) {
        ae2utility$appendButtons();
    }
}
