package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.lhy.ae2utility.compat.WcwtCompat;
import com.lhy.ae2utility.jei.JeiRecipesBatchEncode;

import appeng.menu.me.common.MEStorageMenu;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

@Mixin(targets = "mezz.jei.gui.recipes.RecipeTransferButtonController", remap = false)
public abstract class MixinRecipeTransferButtonController {
    /** Pinned-recipe buttons have no recipes page and must retain JEI's normal transfer behavior. */
    @Unique
    private mezz.jei.gui.recipes.RecipesGui ae2utility$recipesGui;

    @Inject(method = "<init>(Lmezz/jei/api/gui/IRecipeLayoutDrawable;Lmezz/jei/gui/recipes/RecipesGui;Lmezz/jei/common/transfer/RecipeTransferService;)V",
            at = @At("RETURN"))
    private void ae2utility$captureRecipesPage(IRecipeLayoutDrawable<?> recipeLayout,
            mezz.jei.gui.recipes.RecipesGui recipesGui,
            mezz.jei.common.transfer.RecipeTransferService recipeTransferService, CallbackInfo ci) {
        ae2utility$recipesGui = recipesGui;
    }

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void ae2utility$batchEncodeCurrentPageBeforeTransfer(IJeiUserInput input, CallbackInfoReturnable<Boolean> cir) {
        if (ae2utility$recipesGui == null || input.isSimulate() || !Screen.hasShiftDown()) {
            return;
        }

        AbstractContainerMenu parentContainer = ((com.lhy.ae2utility.mixin.RecipesGuiAccessor)(Object)ae2utility$recipesGui).ae2utility$parentMenu();
        if (!(parentContainer instanceof MEStorageMenu)) {
            return;
        }
        if (WcwtCompat.isWcwtMenu(parentContainer)) {
            return;
        }

        JeiRecipesBatchEncode.run(true, true);
        cir.setReturnValue(true);
    }

    @Inject(method = "getTooltips(Lmezz/jei/api/gui/builder/ITooltipBuilder;)V", at = @At("TAIL"))
    private void ae2utility$appendBatchEncodeTooltip(ITooltipBuilder tooltip, CallbackInfo ci) {
        if (ae2utility$recipesGui == null) {
            return;
        }
        AbstractContainerMenu parentContainer = ((com.lhy.ae2utility.mixin.RecipesGuiAccessor)(Object)ae2utility$recipesGui).ae2utility$parentMenu();
        if (parentContainer instanceof MEStorageMenu && !WcwtCompat.isWcwtMenu(parentContainer)) {
            tooltip.add(Component.translatable("jei.tooltip.ae2utility.batch_encode_page"));
            tooltip.add(Component.translatable("jei.tooltip.ae2utility.batch_encode_shift_hint"));
        }
    }
}
