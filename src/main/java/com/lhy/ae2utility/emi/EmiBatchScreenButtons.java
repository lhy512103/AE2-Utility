package com.lhy.ae2utility.emi;

import java.util.List;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.client.Ae2UtilityClientConfig;
import com.lhy.ae2utility.jei.JeiPatternSubstitutionUi;

import appeng.client.gui.Icon;
import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.screen.RecipeScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Adds AE2 Utility's global recipe-screen controls to a visual extension of the EMI
 * recipe panel. The controls remain left-aligned in 18-pixel workstation slots, with
 * the JEI-style icons scaled to fit inside those slots.
 *
 * <p>Rendering and input use NeoForge screen events. The layout does not require a mixin
 * or reflection because the toolbar is an independent extension below EMI's public bounds.</p>
 */
public final class EmiBatchScreenButtons {
    private static final int SLOT_SIZE = 18;
    private static final int GAP = 0;
    private static final int ROW_PADDING = 5;
    private static final int ICON_SIZE = 16;
    private static final int PANEL_EXTENSION = 18;
    private static final int BATCH_ICON_SOURCE_SIZE = 8;

    private static final ResourceLocation EMI_BACKGROUND =
            EmiPort.id("emi", "textures/gui/background.png");
    private static final ResourceLocation TEX_BATCH_PAGE =
            ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "textures/gui/batch_encode_page.png");
    private static final ResourceLocation TEX_BATCH_CATEGORY =
            ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "textures/gui/batch_encode_category.png");

    private enum Button {
        ITEM_SUBSTITUTION,
        FLUID_SUBSTITUTION,
        BATCH_PAGE,
        BATCH_CATEGORY
    }

    private static final Button[] BUTTON_TYPES = new Button[Button.values().length];
    private static final int[] BUTTON_X = new int[Button.values().length];
    private static int buttonY;
    private static int buttonCount;
    private static boolean substitutionContext;

    static {
        clearLayout();
    }

    private EmiBatchScreenButtons() {
    }

    public static void register(net.neoforged.bus.api.IEventBus eventBus) {
        eventBus.addListener(EmiBatchScreenButtons::onRender);
        eventBus.addListener(EmiBatchScreenButtons::onMouseClick);
    }

    private static void onRender(ScreenEvent.Render.Post event) {
        clearLayout();
        if (!(event.getScreen() instanceof RecipeScreen recipeScreen)) {
            return;
        }

        substitutionContext = JeiPatternSubstitutionUi.isSubstitutionContextActive();
        if (substitutionContext) {
            addButton(Button.ITEM_SUBSTITUTION);
            addButton(Button.FLUID_SUBSTITUTION);
        }
        addButton(Button.BATCH_PAGE);
        if (Ae2UtilityClientConfig.showJeiBatchEncodeFullCategoryButton()) {
            addButton(Button.BATCH_CATEGORY);
        }

        Bounds bounds = recipeScreen.getBounds();
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0 || buttonCount == 0) {
            return;
        }

        int originalBottom = bounds.y() + bounds.height();
        Bounds firstWorkstation = recipeScreen.getWorkstationBounds(0);
        int baseX = firstWorkstation.y() == originalBottom - 23
                ? firstWorkstation.x()
                : bounds.x() + ROW_PADDING;
        buttonY = originalBottom - 5;
        for (int i = 0; i < buttonCount; i++) {
            BUTTON_X[i] = baseX + i * (SLOT_SIZE + GAP);
        }

        GuiGraphics graphics = event.getGuiGraphics();
        drawPanelExtension(graphics, bounds, originalBottom);

        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        for (int i = 0; i < buttonCount; i++) {
            drawButton(graphics, BUTTON_TYPES[i], BUTTON_X[i], buttonY, mouseX, mouseY);
        }

        List<Component> tooltip = tooltipAt(mouseX, mouseY);
        if (tooltip != null) {
            graphics.renderTooltip(Minecraft.getInstance().font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    private static void addButton(Button button) {
        BUTTON_TYPES[buttonCount++] = button;
    }

    /**
     * Draws the lower part of EMI's background without a second top border. Starting
     * five pixels above the original bottom covers EMI's existing bottom edge, while
     * retaining its 4-pixel side and bottom corners.
     */
    private static void drawPanelExtension(GuiGraphics graphics, Bounds bounds, int originalBottom) {
        int x = bounds.x();
        int y = originalBottom - 5;
        int width = bounds.width();
        int height = PANEL_EXTENSION + 5;
        int sideWidth = 4;
        int bottomHeight = 4;
        int centerWidth = width - sideWidth * 2;
        int centerHeight = height - bottomHeight;
        if (centerWidth <= 0 || centerHeight <= 0) {
            return;
        }

        EmiDrawContext context = EmiDrawContext.wrap(graphics);
        context.drawTexture(EMI_BACKGROUND, x, y, sideWidth, centerHeight,
                0, 4, sideWidth, 1, 256, 256);
        context.drawTexture(EMI_BACKGROUND, x + sideWidth, y, centerWidth, centerHeight,
                4, 4, 1, 1, 256, 256);
        context.drawTexture(EMI_BACKGROUND, x + width - sideWidth, y, sideWidth, centerHeight,
                5, 4, sideWidth, 1, 256, 256);

        int bottomY = y + centerHeight;
        context.drawTexture(EMI_BACKGROUND, x, bottomY, sideWidth, bottomHeight,
                0, 5, sideWidth, bottomHeight, 256, 256);
        context.drawTexture(EMI_BACKGROUND, x + sideWidth, bottomY, centerWidth, bottomHeight,
                4, 5, 1, bottomHeight, 256, 256);
        context.drawTexture(EMI_BACKGROUND, x + width - sideWidth, bottomY, sideWidth, bottomHeight,
                5, 5, sideWidth, bottomHeight, 256, 256);
    }

    private static void drawButton(GuiGraphics graphics, Button button, int x, int y, int mouseX, int mouseY) {
        boolean hovered = contains(x, y, mouseX, mouseY);
        EmiDrawContext context = EmiDrawContext.wrap(graphics);
        context.drawTexture(EmiRenderHelper.WIDGETS, x, y, SLOT_SIZE, SLOT_SIZE,
                0, 0, SLOT_SIZE, SLOT_SIZE, 256, 256);

        int iconX = x + (SLOT_SIZE - ICON_SIZE) / 2;
        int iconY = y + (SLOT_SIZE - ICON_SIZE) / 2;
        switch (button) {
            case ITEM_SUBSTITUTION -> (JeiPatternSubstitutionUi.isItemSubstituteOn()
                    ? Icon.S_SUBSTITUTION_ENABLED : Icon.S_SUBSTITUTION_DISABLED)
                    .getBlitter().dest(iconX, iconY, ICON_SIZE, ICON_SIZE).blit(graphics);
            case FLUID_SUBSTITUTION -> (JeiPatternSubstitutionUi.isFluidSubstituteOn()
                    ? Icon.S_FLUID_SUBSTITUTION_ENABLED : Icon.S_FLUID_SUBSTITUTION_DISABLED)
                    .getBlitter().dest(iconX, iconY, ICON_SIZE, ICON_SIZE).blit(graphics);
            case BATCH_PAGE -> drawJeiBatchIcon(graphics, TEX_BATCH_PAGE, iconX, iconY, hovered);
            case BATCH_CATEGORY -> drawJeiBatchIcon(graphics, TEX_BATCH_CATEGORY, iconX, iconY, hovered);
        }
        if (hovered) {
            EmiRenderHelper.drawSlotHightlight(context,
                    x + 1, y + 1, SLOT_SIZE - 2, SLOT_SIZE - 2, 200);
        }
    }

    private static void drawJeiBatchIcon(GuiGraphics graphics, ResourceLocation texture,
            int x, int y, boolean hovered) {
        int sourceX = hovered ? BATCH_ICON_SOURCE_SIZE : 0;
        graphics.blit(texture, x, y, ICON_SIZE, ICON_SIZE,
                sourceX, 0, BATCH_ICON_SOURCE_SIZE, BATCH_ICON_SOURCE_SIZE,
                16, 16);
    }

    private static List<Component> tooltipAt(int mouseX, int mouseY) {
        for (int i = 0; i < buttonCount; i++) {
            if (!contains(BUTTON_X[i], buttonY, mouseX, mouseY)) {
                continue;
            }
            return switch (BUTTON_TYPES[i]) {
                case ITEM_SUBSTITUTION -> List.of(
                        Component.translatable(JeiPatternSubstitutionUi.isItemSubstituteOn()
                                ? "gui.tooltips.ae2.SubstitutionsOn"
                                : "gui.tooltips.ae2.SubstitutionsOff"),
                        Component.translatable(JeiPatternSubstitutionUi.isItemSubstituteOn()
                                ? "gui.tooltips.ae2.SubstitutionsDescEnabled"
                                : "gui.tooltips.ae2.SubstitutionsDescDisabled")
                                .withStyle(ChatFormatting.GRAY));
                case FLUID_SUBSTITUTION -> List.of(
                        Component.translatable("gui.tooltips.ae2.FluidSubstitutions"),
                        Component.translatable(JeiPatternSubstitutionUi.isFluidSubstituteOn()
                                ? "gui.tooltips.ae2.FluidSubstitutionsDescEnabled"
                                : "gui.tooltips.ae2.FluidSubstitutionsDescDisabled")
                                .withStyle(ChatFormatting.GRAY));
                case BATCH_PAGE -> List.of(
                        Component.translatable("emi.tooltip.ae2utility.batch_encode_page"),
                        Component.translatable("emi.tooltip.ae2utility.batch_encode_shift_hint")
                                .withStyle(ChatFormatting.GRAY));
                case BATCH_CATEGORY -> List.of(
                        Component.translatable("emi.tooltip.ae2utility.batch_encode_category"),
                        Component.translatable("emi.tooltip.ae2utility.batch_encode_shift_hint")
                                .withStyle(ChatFormatting.GRAY));
            };
        }
        return null;
    }

    private static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0 || !(event.getScreen() instanceof RecipeScreen)) {
            return;
        }
        int mouseX = (int) event.getMouseX();
        int mouseY = (int) event.getMouseY();
        for (int i = 0; i < buttonCount; i++) {
            if (!contains(BUTTON_X[i], buttonY, mouseX, mouseY)) {
                continue;
            }
            Button button = BUTTON_TYPES[i];
            if ((button == Button.ITEM_SUBSTITUTION || button == Button.FLUID_SUBSTITUTION)
                    && !substitutionContext) {
                return;
            }
            playClick();
            switch (button) {
                case ITEM_SUBSTITUTION -> JeiPatternSubstitutionUi.toggleItemSubstitute();
                case FLUID_SUBSTITUTION -> JeiPatternSubstitutionUi.toggleFluidSubstitute();
                case BATCH_PAGE -> EmiRecipesBatchEncode.run(true, Screen.hasShiftDown());
                case BATCH_CATEGORY -> EmiRecipesBatchEncode.run(false, Screen.hasShiftDown());
            }
            event.setCanceled(true);
            return;
        }
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private static boolean contains(int x, int y, int mouseX, int mouseY) {
        return x != Integer.MIN_VALUE
                && mouseX >= x && mouseX < x + SLOT_SIZE
                && mouseY >= y && mouseY < y + SLOT_SIZE;
    }

    private static void clearLayout() {
        for (int i = 0; i < BUTTON_X.length; i++) {
            BUTTON_TYPES[i] = null;
            BUTTON_X[i] = Integer.MIN_VALUE;
        }
        buttonY = 0;
        buttonCount = 0;
        substitutionContext = false;
    }
}
