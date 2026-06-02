package com.lhy.ae2utility.emi;

import java.util.ArrayList;
import java.util.List;

import com.lhy.ae2utility.client.Ae2UtilityClientConfig;

import appeng.client.gui.Icon;
import dev.emi.emi.api.widget.Bounds;
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
 * Injects two "batch encode/upload" buttons into EMI's recipe screen, mirroring the JEI
 * feature: one for every recipe on the current page, one for every recipe across all pages
 * of the focused category tab. Plain click encodes, Shift+click uploads.
 *
 * <p>Uses NeoForge {@link ScreenEvent}s for rendering/clicking (no mixin) and reuses EMI's
 * own button artwork so it stays visually consistent.</p>
 */
public final class EmiBatchScreenButtons {
    private static final int SIZE = 12;
    private static final ResourceLocation EMI_BUTTONS =
            ResourceLocation.fromNamespaceAndPath("emi", "textures/gui/buttons.png");
    private static final int BLANK_BUTTON_U = 72;

    // Last computed button rectangles (screen-space), or x = MIN_VALUE when not shown.
    private static int pageX = Integer.MIN_VALUE;
    private static int pageY;
    private static int categoryX = Integer.MIN_VALUE;
    private static int categoryY;

    private EmiBatchScreenButtons() {
    }

    public static void register(net.neoforged.bus.api.IEventBus eventBus) {
        eventBus.addListener(EmiBatchScreenButtons::onRender);
        eventBus.addListener(EmiBatchScreenButtons::onMouseClick);
    }

    private static void onRender(ScreenEvent.Render.Post event) {
        pageX = Integer.MIN_VALUE;
        categoryX = Integer.MIN_VALUE;
        if (!(event.getScreen() instanceof RecipeScreen recipeScreen)) {
            return;
        }
        Bounds bounds = recipeScreen.getBounds();
        if (bounds == null) {
            return;
        }
        boolean showCategory = Ae2UtilityClientConfig.showJeiBatchEncodeFullCategoryButton();

        int baseX = bounds.x() + 2;
        int baseY = bounds.y() + bounds.height() + 2;
        pageX = baseX;
        pageY = baseY;
        if (showCategory) {
            categoryX = baseX + SIZE + 2;
            categoryY = baseY;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();

        drawButton(graphics, pageX, pageY, mouseX, mouseY, false);
        if (showCategory) {
            drawButton(graphics, categoryX, categoryY, mouseX, mouseY, true);
        }

        List<Component> tooltip = tooltipAt(mouseX, mouseY);
        if (tooltip != null) {
            graphics.renderTooltip(Minecraft.getInstance().font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    private static void drawButton(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, boolean category) {
        boolean hovered = contains(x, y, mouseX, mouseY);
        int v = hovered ? SIZE : 0;
        graphics.blit(EMI_BUTTONS, x, y, SIZE, SIZE, (float) BLANK_BUTTON_U, (float) v, SIZE, SIZE, 256, 256);
        Icon.ARROW_UP.getBlitter().dest(x + 1, y + 1, 10, 10).blit(graphics);
        // small corner marker distinguishes the "all pages" button from the "current page" one
        if (category) {
            graphics.fill(x + SIZE - 4, y + SIZE - 4, x + SIZE - 1, y + SIZE - 1, 0xFFFFAA00);
        }
    }

    private static List<Component> tooltipAt(int mouseX, int mouseY) {
        if (pageX != Integer.MIN_VALUE && contains(pageX, pageY, mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("emi.tooltip.ae2utility.batch_encode_page"));
            lines.add(Component.translatable("emi.tooltip.ae2utility.batch_encode_shift_hint").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        if (categoryX != Integer.MIN_VALUE && contains(categoryX, categoryY, mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("emi.tooltip.ae2utility.batch_encode_category"));
            lines.add(Component.translatable("emi.tooltip.ae2utility.batch_encode_shift_hint").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        return null;
    }

    private static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0 || !(event.getScreen() instanceof RecipeScreen)) {
            return;
        }
        int mouseX = (int) event.getMouseX();
        int mouseY = (int) event.getMouseY();
        boolean shiftUpload = Screen.hasShiftDown();
        if (pageX != Integer.MIN_VALUE && contains(pageX, pageY, mouseX, mouseY)) {
            playClick();
            EmiRecipesBatchEncode.run(true, shiftUpload);
            event.setCanceled(true);
        } else if (categoryX != Integer.MIN_VALUE && contains(categoryX, categoryY, mouseX, mouseY)) {
            playClick();
            EmiRecipesBatchEncode.run(false, shiftUpload);
            event.setCanceled(true);
        }
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    private static boolean contains(int x, int y, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE;
    }
}
