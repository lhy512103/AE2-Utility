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
import net.minecraft.ChatFormatting;
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
 * by appending a {@link ButtonDefinition}, without touching the render/click
 * control flow.</p>
 */
public final class JeiPatternSubstitutionOptionsOverlay {
    private static final int BUTTON_SIZE = 16;
    private static final int ICON_SIZE = 14;
    private static final int SMALL_ICON_SIZE = 8;
    private static final int BUTTON_BORDER_SIZE = 1;
    private static final int BORDER_SIZE = 5;
    private static final int OVERLAP_SIZE = 6;
    private static final int BACKGROUND_JOIN_OVERLAP = 1;
    private static final int OPTION_TAB_WIDTH = (2 * BUTTON_BORDER_SIZE) + (BORDER_SIZE * 2) + BUTTON_SIZE;
    private static final int JEI_OPTION_BUTTON_COUNT = 2;

    private static final List<ButtonDefinition> BUTTONS = List.of(
            new ButtonDefinition(
                    ButtonIcon.BATCH_PAGE,
                    Component.translatable("jei.tooltip.ae2utility.batch_encode_page"),
                    () -> JeiRecipesBatchEncode.run(true, Screen.hasShiftDown()),
                    true),
            new ButtonDefinition(
                    ButtonIcon.BATCH_CATEGORY,
                    Component.translatable("jei.tooltip.ae2utility.batch_encode_category"),
                    () -> JeiRecipesBatchEncode.run(false, Screen.hasShiftDown()),
                    true),
            new ButtonDefinition(
                    Icon.SUBSTITUTION_DISABLED,
                    Icon.SUBSTITUTION_ENABLED,
                    Component.translatable("jei.tooltip.ae2utility.item_substitution.disabled"),
                    Component.translatable("jei.tooltip.ae2utility.item_substitution.enabled"),
                    JeiPatternSubstitutionUi::isItemSubstituteOn,
                    JeiPatternSubstitutionUi::toggleItemSubstitute),
            new ButtonDefinition(
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
        for (int i = 0; i < BUTTONS.size(); i++) {
            ButtonDefinition button = BUTTONS.get(i);
            int buttonY = area.getY() + BORDER_SIZE + i * BUTTON_SIZE + BUTTON_BORDER_SIZE;
            boolean hovered = mouseX >= buttonX && mouseX < buttonX + BUTTON_SIZE
                    && mouseY >= buttonY && mouseY < buttonY + BUTTON_SIZE;
            boolean enabled = button.isOn();

            drawButton(guiGraphics, buttonX, buttonY, hovered, enabled, button.icon(enabled));

            if (hovered) {
                guiGraphics.renderComponentTooltip(Minecraft.getInstance().font, button.tooltip(enabled), mouseX, mouseY);
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
        for (int i = BUTTONS.size() - 1; i >= 0; i--) {
            int buttonY = area.getY() + BORDER_SIZE + i * BUTTON_SIZE + BUTTON_BORDER_SIZE;
            if (contains(buttonX, buttonY, mouseX, mouseY)) {
                BUTTONS.get(i).press().run();
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
        int height = (2 * BUTTON_BORDER_SIZE) + (BORDER_SIZE * 2) + (BUTTONS.size() * BUTTON_SIZE);
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

    private static void drawButton(GuiGraphics guiGraphics, int x, int y, boolean hovered, boolean pressed, ButtonIcon icon) {
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

        int iconSize = icon.small() ? SMALL_ICON_SIZE : ICON_SIZE;
        int iconOffset = (BUTTON_SIZE - iconSize) / 2;
        if (pressed) {
            iconOffset++;
        }
        icon.draw(guiGraphics, x + iconOffset, y + iconOffset, iconSize, iconSize);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private record ButtonDefinition(
            ButtonIcon offIcon,
            ButtonIcon onIcon,
            Component disabledTooltip,
            Component enabledTooltip,
            BooleanSupplier state,
            Runnable press,
            boolean showShiftHint) {
        ButtonDefinition(Icon offIcon, Icon onIcon, Component disabledTooltip, Component enabledTooltip,
                BooleanSupplier state, Runnable press) {
            this(ButtonIcon.of(offIcon), ButtonIcon.of(onIcon), disabledTooltip, enabledTooltip, state, press, false);
        }

        ButtonDefinition(ButtonIcon icon, Component tooltip, Runnable press, boolean showShiftHint) {
            this(icon, icon, tooltip, tooltip, () -> false, press, showShiftHint);
        }

        boolean isOn() {
            return state.getAsBoolean();
        }

        ButtonIcon icon(boolean enabled) {
            return enabled ? onIcon : offIcon;
        }

        List<Component> tooltip(boolean enabled) {
            Component main = enabled ? enabledTooltip : disabledTooltip;
            if (showShiftHint) {
                return List.of(main,
                        Component.translatable("jei.tooltip.ae2utility.batch_encode_shift_hint")
                                .withStyle(ChatFormatting.GRAY));
            }
            return List.of(main);
        }
    }

    private enum ButtonIcon {
        AE_SUBSTITUTION_OFF(Icon.SUBSTITUTION_DISABLED, false),
        AE_SUBSTITUTION_ON(Icon.SUBSTITUTION_ENABLED, false),
        AE_FLUID_SUBSTITUTION_OFF(Icon.FLUID_SUBSTITUTION_DISABLED, false),
        AE_FLUID_SUBSTITUTION_ON(Icon.FLUID_SUBSTITUTION_ENABLED, false),
        BATCH_PAGE(null, true),
        BATCH_CATEGORY(null, true);

        private final Icon aeIcon;
        private final boolean small;

        ButtonIcon(Icon aeIcon, boolean small) {
            this.aeIcon = aeIcon;
            this.small = small;
        }

        static ButtonIcon of(Icon icon) {
            if (icon == Icon.SUBSTITUTION_DISABLED) {
                return AE_SUBSTITUTION_OFF;
            }
            if (icon == Icon.SUBSTITUTION_ENABLED) {
                return AE_SUBSTITUTION_ON;
            }
            if (icon == Icon.FLUID_SUBSTITUTION_DISABLED) {
                return AE_FLUID_SUBSTITUTION_OFF;
            }
            if (icon == Icon.FLUID_SUBSTITUTION_ENABLED) {
                return AE_FLUID_SUBSTITUTION_ON;
            }
            throw new IllegalArgumentException("Unsupported JEI option icon: " + icon);
        }

        boolean small() {
            return small;
        }

        void draw(GuiGraphics guiGraphics, int x, int y, int width, int height) {
            if (aeIcon != null) {
                aeIcon.getBlitter().dest(x, y, width, height).blit(guiGraphics);
                return;
            }
            if (this == BATCH_PAGE) {
                drawPageBatchIcon(guiGraphics, x, y, width, height);
            } else if (this == BATCH_CATEGORY) {
                drawCategoryBatchIcon(guiGraphics, x, y, width, height);
            }
        }
    }

    private static void drawPageBatchIcon(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        int color = 0xFFFFFFFF;
        int left = x + Math.max(1, width / 8);
        int top = y + Math.max(1, height / 8);
        int right = x + width - Math.max(1, width / 8);
        int bottom = y + height - Math.max(1, height / 8);
        guiGraphics.fill(left, top, right, bottom, color);
        guiGraphics.fill(left + 1, top + 1, right - 1, bottom - 1, 0xFF555555);
        guiGraphics.fill(x + width / 2 - 1, y + 2, x + width / 2 + 1, y + height - 2, color);
        guiGraphics.fill(x + width / 2 - 3, y + 4, x + width / 2 + 3, y + 5, color);
        guiGraphics.fill(x + width / 2 - 4, y + 5, x + width / 2 + 4, y + 6, color);
    }

    private static void drawCategoryBatchIcon(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        int color = 0xFFFFFFFF;
        int cell = Math.max(2, width / 3);
        guiGraphics.fill(x + 1, y + 1, x + 1 + cell, y + 1 + cell, color);
        guiGraphics.fill(x + width - 1 - cell, y + 1, x + width - 1, y + 1 + cell, color);
        guiGraphics.fill(x + 1, y + height - 1 - cell, x + 1 + cell, y + height - 1, color);
        guiGraphics.fill(x + width - 1 - cell, y + height - 1 - cell, x + width - 1, y + height - 1, color);
        guiGraphics.fill(x + width / 2 - 1, y + 2, x + width / 2 + 1, y + height - 2, color);
    }

    private static boolean contains(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
    }
}
