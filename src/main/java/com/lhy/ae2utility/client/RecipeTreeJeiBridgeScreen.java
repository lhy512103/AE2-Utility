package com.lhy.ae2utility.client;

import org.jetbrains.annotations.Nullable;

import com.lhy.ae2utility.jei.Ae2UtilityJeiPlugin;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

public class RecipeTreeJeiBridgeScreen extends AbstractContainerScreen<RecipeTreeTransferMenu> implements RecipeTreeJeiTransferTarget {
    private final RecipeTreeJeiTransferTarget delegate;
    private final Screen returnScreen;
    private final ITypedIngredient<?> ingredient;
    private boolean attemptedOpen;
    private boolean recipeApplied;

    public RecipeTreeJeiBridgeScreen(RecipeTreeJeiTransferTarget delegate, Screen returnScreen, ITypedIngredient<?> ingredient) {
        super(new RecipeTreeTransferMenu(), RecipeTreeTransferMenu.ae2utility$getPlayerInventory(), Component.empty());
        this.delegate = delegate;
        this.returnScreen = returnScreen;
        this.ingredient = ingredient;
        this.menu.ae2utility$setTarget(this);
    }

    @Override
    protected void init() {
        super.init();
        this.imageWidth = this.width;
        this.imageHeight = this.height;
        this.leftPos = 0;
        this.topPos = 0;
        openJeiOnce();
    }

    private void openJeiOnce() {
        if (attemptedOpen) {
            return;
        }
        attemptedOpen = true;
        IJeiRuntime runtime = Ae2UtilityJeiPlugin.getJeiRuntime();
        if (runtime == null) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(returnScreen);
            }
            return;
        }
        IFocusFactory focusFactory = runtime.getJeiHelpers().getFocusFactory();
        runtime.getRecipesGui().show(createFocus(focusFactory, ingredient));
    }

    private static IFocus<?> createFocus(IFocusFactory focusFactory, ITypedIngredient<?> ingredient) {
        return createFocusTyped(focusFactory, ingredient);
    }

    private static <T> IFocus<T> createFocusTyped(IFocusFactory focusFactory, ITypedIngredient<?> ingredient) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> typed = (ITypedIngredient<T>) ingredient;
        return focusFactory.createFocus(RecipeIngredientRole.OUTPUT, typed);
    }

    @Override
    public void ae2utility$applyJeiRecipe(Object recipe, IRecipeSlotsView recipeSlots) {
        delegate.ae2utility$applyJeiRecipe(recipe, recipeSlots);
        recipeApplied = true;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (attemptedOpen && this.minecraft != null && this.minecraft.screen == this) {
            this.minecraft.setScreen(returnScreen);
            return;
        }
        if (recipeApplied && this.minecraft != null && this.minecraft.screen != this) {
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
