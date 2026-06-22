package com.lhy.ae2utility.jei;

import java.util.List;
import java.util.function.BooleanSupplier;

import appeng.client.gui.Icon;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.elements.DrawableNineSliceTexture;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Adds AE2 substitution toggles next to JEI's recipe option buttons.
 *
 * <p>JEI 15.x does not expose a public recipe-options button registry. Keep this
 * as a small data-driven overlay so additional AE2 Utility buttons can be added
 * by appending a {@link ToggleDefinition}, without touching the render/click
 * control flow.</p>
 */
public final class JeiPatternSubstitutionOptionsOverlay {
    private static final int BUTTON_SIZE = 16;
    private static final int ICON_SIZE = 14;
    private static final int BUTTON_BORDER_SIZE = 1;
    private static final int BORDER_SIZE = 5;
    private static final int OVERLAP_SIZE = 6;
    private static final int BACKGROUND_JOIN_OVERLAP = 1;
    private static final int OPTION_TAB_WIDTH = (2 * BUTTON_BORDER_SIZE) + (BORDER_SIZE * 2) + BUTTON_SIZE;
    private static final int JEI_OPTION_BUTTON_COUNT = 2;

    private static final List<ToggleDefinition> TOGGLES = List.of(
            new ToggleDefinition(
                    Icon.SUBSTITUTION_DISABLED,
                    Icon.SUBSTITUTION_ENABLED,
                    Component.translatable("jei.tooltip.ae2utility.item_substitution.disabled"),
                    Component.translatable("jei.tooltip.ae2utility.item_substitution.enabled"),
                    JeiPatternSubstitutionUi::isItemSubstituteOn,
                    JeiPatternSubstitutionUi::toggleItemSubstitute),
            new ToggleDefinition(
                    Icon.FLUID_SUBSTITUTION_DISABLED,
                    Icon.FLUID_SUBSTITUTION_ENABLED,
                    Component.translatable("jei.tooltip.ae2utility.fluid_substitution.disabled"),
                    Component.translatable("jei.tooltip.ae2utility.fluid_substitution.enabled"),
                    JeiPatternSubstitutionUi::isFluidSubstituteOn,
                    JeiPatternSubstitutionUi::toggleFluidSubstitute));

    private JeiPatternSubstitutionOptionsOverlay() {
    }

    public static void clearActiveButtons() {
    }

    public static void render(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!(screen instanceof RecipesGui recipesGui)) {
            return;
        }

        ImmutableRect2i area = computeArea(recipesGui);
        drawBackground(guiGraphics, area);

        int buttonX = area.getX() + BORDER_SIZE + BUTTON_BORDER_SIZE;
        for (int i = 0; i < TOGGLES.size(); i++) {
            ToggleDefinition toggle = TOGGLES.get(i);
            int buttonY = area.getY() + BORDER_SIZE + i * BUTTON_SIZE + BUTTON_BORDER_SIZE;
            boolean hovered = mouseX >= buttonX && mouseX < buttonX + BUTTON_SIZE
                    && mouseY >= buttonY && mouseY < buttonY + BUTTON_SIZE;
            boolean enabled = toggle.isOn();

            drawButton(guiGraphics, buttonX, buttonY, hovered, enabled, enabled ? toggle.onIcon() : toggle.offIcon());

            if (hovered) {
                guiGraphics.renderComponentTooltip(Minecraft.getInstance().font,
                        List.of(enabled ? toggle.enabledTooltip() : toggle.disabledTooltip()), mouseX, mouseY);
            }
        }
    }

    public static boolean pressIfHovered(double mouseX, double mouseY) {
        Screen screen = Minecraft.getInstance().screen;
        if (!(screen instanceof RecipesGui recipesGui)) {
            return false;
        }

        ImmutableRect2i area = computeArea(recipesGui);
        int buttonX = area.getX() + BORDER_SIZE + BUTTON_BORDER_SIZE;
        for (int i = TOGGLES.size() - 1; i >= 0; i--) {
            int buttonY = area.getY() + BORDER_SIZE + i * BUTTON_SIZE + BUTTON_BORDER_SIZE;
            if (contains(buttonX, buttonY, mouseX, mouseY)) {
                TOGGLES.get(i).toggle().run();
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return false;
    }

    private static ImmutableRect2i computeArea(RecipesGui recipesGui) {
        ImmutableRect2i recipeArea = recipesGui.getArea();
        int width = OPTION_TAB_WIDTH;
        int height = (2 * BUTTON_BORDER_SIZE) + (BORDER_SIZE * 2) + (TOGGLES.size() * BUTTON_SIZE);
        int x = recipeArea.getX() - width + OVERLAP_SIZE;
        int jeiOptionsHeight = (2 * BUTTON_BORDER_SIZE) + (BORDER_SIZE * 2) + (JEI_OPTION_BUTTON_COUNT * BUTTON_SIZE);
        int y = recipeArea.getY() + recipeArea.getHeight() - jeiOptionsHeight - height + BACKGROUND_JOIN_OVERLAP;
        return new ImmutableRect2i(x, y, width, height);
    }

    private static void drawBackground(GuiGraphics guiGraphics, ImmutableRect2i area) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableDepthTest();
        Internal.getTextures().getRecipeOptionsTab().draw(guiGraphics, area);
        RenderSystem.enableDepthTest();
    }

    private static void drawButton(GuiGraphics guiGraphics, int x, int y, boolean hovered, boolean pressed, Icon icon) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        DrawableNineSliceTexture texture = Internal.getTextures().getButtonForState(pressed, true, hovered);
        texture.draw(guiGraphics, x, y, BUTTON_SIZE, BUTTON_SIZE);

        int iconOffset = (BUTTON_SIZE - ICON_SIZE) / 2;
        if (pressed) {
            iconOffset++;
        }
        icon.getBlitter().dest(x + iconOffset, y + iconOffset, ICON_SIZE, ICON_SIZE).blit(guiGraphics);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private record ToggleDefinition(
            Icon offIcon,
            Icon onIcon,
            Component disabledTooltip,
            Component enabledTooltip,
            BooleanSupplier state,
            Runnable toggle) {
        boolean isOn() {
            return state.getAsBoolean();
        }
    }

    private static boolean contains(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
    }
}
