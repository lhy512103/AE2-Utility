package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.lhy.ae2utility.jei.RecipeTreeOpenHelper;

import appeng.menu.me.common.MEStorageMenu;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;

@Mixin(targets = "mezz.jei.gui.recipes.RecipeTransferButtonController", remap = false)
public abstract class MixinRecipeTransferButtonController {
    @Shadow
    private IRecipeLayoutDrawable<?> recipeLayout;

    @Shadow
    private mezz.jei.gui.recipes.RecipesGui recipesGui;

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void ae2utility$openRecipeTreeBeforeTransfer(IJeiUserInput input, CallbackInfoReturnable<Boolean> cir) {
        if (input.isSimulate() || !Screen.hasShiftDown()) {
            return;
        }

        AbstractContainerMenu parentContainer = recipesGui.getParentContainerMenu();
        if (!(parentContainer instanceof MEStorageMenu)) {
            return;
        }

        RecipeTreeOpenHelper.openFromLayout(recipeLayout, Minecraft.getInstance().screen);
        cir.setReturnValue(true);
    }

    @Inject(method = "getTooltips", at = @At("TAIL"))
    private void ae2utility$appendRecipeTreeTooltip(ITooltipBuilder tooltip, CallbackInfo ci) {
        AbstractContainerMenu parentContainer = recipesGui.getParentContainerMenu();
        if (parentContainer instanceof MEStorageMenu) {
            tooltip.add(Component.translatable("jei.tooltip.ae2utility.transfer_shift_tree"));
        }
    }
}
