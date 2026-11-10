package com.lhy.ae2utility.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;

import com.lhy.ae2utility.client.jei.EncodePatternRecipeLayoutContext;
import com.lhy.ae2utility.jei.EncodePatternButtonController;

/**
 * 悬停本模组 JEI 编码箭头时，INPUT 槽 {@code getDisplayedIngredient} 直接返回已缓存的实际编码材料。
 * 只走 {@link IRecipeSlotView} 公开 API，避免依赖 JEI 内部字段。
 */
@Mixin(targets = "mezz.jei.library.gui.ingredients.RecipeSlot", remap = false)
public class MixinRecipeSlotEncodeShiftPin {

    @Inject(method = "getDisplayedIngredient", at = @At("HEAD"), cancellable = true)
    private void ae2utility$pinEncodeDisplayedIngredient(CallbackInfoReturnable<Optional<ITypedIngredient<?>>> cir) {
        IRecipeSlotView slot = (IRecipeSlotView) (Object) this;
        if (slot.getRole() != RecipeIngredientRole.INPUT) {
            return;
        }
        ITypedIngredient<?> pinned = EncodePatternButtonController.peekPinnedDisplayedIngredient(
                EncodePatternRecipeLayoutContext.get(), slot);
        if (pinned != null) {
            cir.setReturnValue(Optional.of(pinned));
        }
    }
}
