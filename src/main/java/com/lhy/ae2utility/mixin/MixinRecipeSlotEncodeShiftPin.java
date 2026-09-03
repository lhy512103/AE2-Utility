package com.lhy.ae2utility.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.library.gui.ingredients.RecipeSlotIngredients;

import com.lhy.ae2utility.client.jei.EncodePatternRecipeLayoutContext;
import com.lhy.ae2utility.jei.EncodePatternButtonController;

/**
 * 悬停本模组 JEI 编码箭头时，INPUT 槽 {@code getDisplayedIngredient}
 * 直接返回已缓存的实际编码材料。
 * 热路径只做角色判断和缓存查找，不在此处选股或遍历候选。
 */
@Mixin(targets = "mezz.jei.library.gui.ingredients.RecipeSlot", remap = false)
public class MixinRecipeSlotEncodeShiftPin {

    @Shadow
    @Final
    private RecipeIngredientRole role;

    @Shadow
    @Final
    private RecipeSlotIngredients ingredients;

    @Inject(
            method = "getDisplayedIngredient",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ae2utility$pinEncodeDisplayedIngredient(
            CallbackInfoReturnable<Optional<ITypedIngredient<?>>> cir) {

        if (this.ingredients.hasDisplayOverrides()
                || this.role != RecipeIngredientRole.INPUT) {
            return;
        }

        ITypedIngredient<?> pinned =
                EncodePatternButtonController.peekPinnedDisplayedIngredient(
                        EncodePatternRecipeLayoutContext.get(),
                        (IRecipeSlotView) (Object) this
                );

        if (pinned != null) {
            cir.setReturnValue(Optional.of(pinned));
        }
    }
}