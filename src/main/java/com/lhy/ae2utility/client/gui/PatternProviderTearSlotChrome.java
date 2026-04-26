package com.lhy.ae2utility.client.gui;

import net.minecraft.client.gui.GuiGraphics;

import appeng.client.gui.style.Blitter;

/**
 * Draws the same slot chrome as AE2 {@link appeng.client.gui.widgets.UpgradesPanel} for a single 18×18 cell,
 * so the dedicated NBT tear slot matches EAEP upgrade card slots visually.
 */
public final class PatternProviderTearSlotChrome {

    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 5;

    private static final Blitter BACKGROUND = Blitter.texture("guis/extra_panels.png", 128, 128);

    private PatternProviderTearSlotChrome() {
    }

    /**
     * @param innerLeft absolute screen X of the slot content (same as {@code guiLeft + slot.x})
     * @param innerTop absolute screen Y of the slot content (same as {@code guiTop + slot.y})
     */
    public static void draw(GuiGraphics graphics, int innerLeft, int innerTop) {
        int slotOriginX = innerLeft;
        int slotOriginY = innerTop;
        int slotCount = 1;

        drawSlot(graphics, slotOriginX, slotOriginY, true, true, true, true);

        guiGraphicsDecorLines(graphics, slotOriginX, slotOriginY, slotCount);
    }

    private static void guiGraphicsDecorLines(GuiGraphics guiGraphics, int slotOriginX, int slotOriginY, int slotCount) {
        guiGraphics.hLine(slotOriginX - 4, slotOriginX + 11, slotOriginY, 0XFFf2f2f2);
        guiGraphics.hLine(slotOriginX - 4, slotOriginX + 11, slotOriginY + (SLOT_SIZE * slotCount) - 1, 0XFFf2f2f2);
        guiGraphics.vLine(slotOriginX - 5, slotOriginY - 1, slotOriginY + (SLOT_SIZE * slotCount), 0XFFf2f2f2);
        guiGraphics.vLine(slotOriginX + 12, slotOriginY - 1, slotOriginY + (SLOT_SIZE * slotCount), 0XFFf2f2f2);
    }

    /** Mirrors {@link appeng.client.gui.widgets.UpgradesPanel}. */
    private static void drawSlot(GuiGraphics guiGraphics, int x, int y,
            boolean borderLeft, boolean borderTop, boolean borderRight, boolean borderBottom) {
        int srcX = PADDING;
        int srcY = PADDING;
        int srcWidth = SLOT_SIZE;
        int srcHeight = SLOT_SIZE;

        if (borderLeft) {
            x -= PADDING;
            srcX = 0;
            srcWidth += PADDING;
        }
        if (borderRight) {
            srcWidth += PADDING;
        }
        if (borderTop) {
            y -= PADDING;
            srcY = 0;
            srcHeight += PADDING;
        }
        if (borderBottom) {
            srcHeight += PADDING + 2;
        }

        BACKGROUND.src(srcX, srcY, srcWidth, srcHeight)
                .dest(x, y)
                .blit(guiGraphics);
    }
}
