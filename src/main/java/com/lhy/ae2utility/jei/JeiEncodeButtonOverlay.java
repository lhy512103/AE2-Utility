package com.lhy.ae2utility.jei;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.lhy.ae2utility.Ae2UtilityMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

import mezz.jei.api.gui.IRecipeLayoutDrawable;

/**
 * Forge 1.20.1 的 JEI 没有 1.21.1 的全局 recipe-button API。
 * 这里直接读取 JEI 当前可见的 recipe layout，给所有非 tag 配方补同一个编码按钮。
 */
public final class JeiEncodeButtonOverlay {
    private static final String RECIPES_GUI_CLASS = "mezz.jei.gui.recipes.RecipesGui";
    private static final int BUTTON_SIZE = 13;
    private static final int BUTTON_SPACING = 2;

    private static @Nullable Field layoutsField;
    private static @Nullable Field recipeLayoutsWithButtonsField;
    private static @Nullable Method recipeLayoutMethod;
    private static boolean reflectionFailed;

    private JeiEncodeButtonOverlay() {
    }

    public static void render(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (screen == null || !RECIPES_GUI_CLASS.equals(screen.getClass().getName())) {
            return;
        }
        if (!EncodePatternButtonController.isAvailable()) {
            return;
        }

        List<?> visibleLayouts = getVisibleRecipeLayouts(screen);
        if (visibleLayouts == null || visibleLayouts.isEmpty()) {
            return;
        }

        for (Object layoutWithButtons : visibleLayouts) {
            IRecipeLayoutDrawable<?> recipeLayout = getRecipeLayout(layoutWithButtons);
            if (recipeLayout != null) {
                drawForRecipeLayout(recipeLayout, guiGraphics, mouseX, mouseY);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static @Nullable List<?> getVisibleRecipeLayouts(Screen screen) {
        if (reflectionFailed) {
            return null;
        }

        try {
            if (layoutsField == null) {
                layoutsField = screen.getClass().getDeclaredField("layouts");
                layoutsField.setAccessible(true);
            }
            Object layouts = layoutsField.get(screen);
            if (layouts == null) {
                return null;
            }

            if (recipeLayoutsWithButtonsField == null) {
                recipeLayoutsWithButtonsField = layouts.getClass().getDeclaredField("recipeLayoutsWithButtons");
                recipeLayoutsWithButtonsField.setAccessible(true);
            }
            Object value = recipeLayoutsWithButtonsField.get(layouts);
            return value instanceof List<?> list ? list : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            Ae2UtilityMod.LOGGER.warn("Failed to access JEI recipe layouts for encode button overlay", e);
            return null;
        }
    }

    private static @Nullable IRecipeLayoutDrawable<?> getRecipeLayout(Object layoutWithButtons) {
        if (layoutWithButtons == null || reflectionFailed) {
            return null;
        }

        try {
            if (recipeLayoutMethod == null) {
                recipeLayoutMethod = layoutWithButtons.getClass().getDeclaredMethod("recipeLayout");
                recipeLayoutMethod.setAccessible(true);
            }
            Object value = recipeLayoutMethod.invoke(layoutWithButtons);
            return value instanceof IRecipeLayoutDrawable<?> recipeLayout ? recipeLayout : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            Ae2UtilityMod.LOGGER.warn("Failed to access JEI recipe layout for encode button overlay", e);
            return null;
        }
    }

    private static void drawForRecipeLayout(IRecipeLayoutDrawable<?> recipeLayout, GuiGraphics guiGraphics,
            int mouseX, int mouseY) {
        if (EncodePatternButtonController.isTagRecipe(recipeLayout.getRecipeCategory())) {
            return;
        }

        Rect2i recipeRect = recipeLayout.getRect();
        Rect2i bookmarkArea = recipeLayout.getRecipeBookmarkButtonArea();
        int buttonX = recipeRect.getX() + bookmarkArea.getX();
        int buttonY = recipeRect.getY() + bookmarkArea.getY() - bookmarkArea.getHeight() - BUTTON_SPACING;

        boolean hovered = mouseX >= buttonX && mouseX < buttonX + BUTTON_SIZE
                && mouseY >= buttonY && mouseY < buttonY + BUTTON_SIZE;
        int backdropColor = EncodePatternButtonController.computeButtonBackdropColor(
                recipeLayout.getRecipe(), recipeLayout.getRecipeSlotsView());

        EncodePatternButtonController.drawButton(guiGraphics, buttonX, buttonY, BUTTON_SIZE, BUTTON_SIZE,
                backdropColor, hovered);
        EncodePatternButtonState.setActiveButton(recipeLayout, buttonX, buttonY, BUTTON_SIZE, BUTTON_SIZE);

        if (hovered) {
            var poseStack = guiGraphics.pose();
            poseStack.pushPose();
            poseStack.translate(recipeRect.getX(), recipeRect.getY(), 0);
            EncodePatternButtonController.drawSlotHighlights(
                    guiGraphics, recipeLayout.getRecipe(), recipeLayout.getRecipeSlotsView());
            poseStack.popPose();
            drawTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    private static void drawTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        List<Component> lines = EncodePatternButtonController.getTooltipLines();
        if (!lines.isEmpty()) {
            guiGraphics.renderComponentTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY);
        }
    }
}
