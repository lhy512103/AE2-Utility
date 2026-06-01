package com.lhy.ae2utility.mixin;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;

import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.library.ingredients.DisplayIngredientAcceptor;

import com.lhy.ae2utility.client.jei.EncodePatternPreviewStorage;
import com.lhy.ae2utility.client.jei.EncodePatternRecipeLayoutContext;
import com.lhy.ae2utility.network.RecipeTransferPacketHelper;
import com.lhy.ae2utility.util.EncodePatternInputChooser;

/**
 * 样板编码终端 + JEI：鼠标悬停在本栏编码按钮或任意原料槽上时，
 * INPUT 槽 {@link RecipeSlot#getDisplayedIngredient} 与发包选股一致（当前默认关闭，见 {@link EncodePatternPreviewStorage#isShiftPinIngredientOverrideActive}）。
 */
@Mixin(targets = "mezz.jei.library.gui.ingredients.RecipeSlot", remap = false)
public class MixinRecipeSlotEncodeShiftPin {

    @Shadow
    @Final
    private RecipeIngredientRole role;

    @Shadow
    @Nullable
    private DisplayIngredientAcceptor displayOverrides;

    @Inject(method = "getDisplayedIngredient", at = @At("HEAD"), cancellable = true)
    private void ae2utility$pickEncodeDisplayedIngredientWhenShift(CallbackInfoReturnable<Optional<ITypedIngredient<?>>> cir) {
        if (this.displayOverrides != null) {
            return;
        }
        if (this.role != RecipeIngredientRole.INPUT) {
            return;
        }
        if (!EncodePatternPreviewStorage.isShiftPinIngredientOverrideActive()) {
            return;
        }
        if (!EncodePatternPreviewStorage.isPatternEncodingRecipesGui()) {
            return;
        }
        IRecipeSlotView view = (IRecipeSlotView) (Object) this;
        IRecipeLayoutDrawable<?> layoutCtx = EncodePatternRecipeLayoutContext.resolveLayoutForEncodeSlotTooltip(view);
        if (layoutCtx == null) {
            return;
        }
        if (!EncodePatternRecipeLayoutContext.slotBelongsToLayout(layoutCtx, view)) {
            return;
        }
        if (!EncodePatternPreviewStorage.shouldPinDisplayedIngredient(layoutCtx, view)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        List<GenericStack> alts = RecipeTransferPacketHelper.collectEncodeAlternativesForInputSlot(view);
        MEStorage me = EncodePatternPreviewStorage.resolveMeStorage(mc);
        GenericStack chosen = EncodePatternInputChooser.pickEncodedInput(alts, me,
                com.lhy.ae2utility.jei.CraftableStateCache::isCraftable,
                EncodePatternPreviewStorage.jeiEncodePreserveInputOrder());
        if (chosen == null) {
            return;
        }
        Optional<ITypedIngredient<?>> typed = RecipeTransferPacketHelper.findTypedIngredientMatchingChosen(view, chosen);
        if (typed.isEmpty()) {
            return;
        }
        cir.setReturnValue(typed);
        cir.cancel();
    }
}
