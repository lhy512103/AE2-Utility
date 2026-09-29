package com.lhy.ae2utility.mixin.jei;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.lhy.ae2utility.jei.EncodePreviewCache;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;

/** 悬停编码箭头时，让多候选输入槽固定显示实际会写入样板的材料。 */
@Mixin(targets = "mezz.jei.library.gui.ingredients.RecipeSlot", remap = false)
public abstract class RecipeSlotPinMixin {
    @Inject(method = "getDisplayedIngredient()Ljava/util/Optional;", at = @At("HEAD"), cancellable = true)
    private void ae2utility$pinEncodedIngredient(CallbackInfoReturnable<Optional<ITypedIngredient<?>>> cir) {
        ITypedIngredient<?> pinned = EncodePreviewCache.pinnedIngredient((IRecipeSlotView) (Object) this);
        if (pinned != null) {
            cir.setReturnValue(Optional.of(pinned));
        }
    }
}
