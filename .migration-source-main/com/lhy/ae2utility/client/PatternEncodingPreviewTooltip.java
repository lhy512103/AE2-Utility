package com.lhy.ae2utility.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import com.lhy.ae2utility.util.PatternEncodingPreview;

public final class PatternEncodingPreviewTooltip implements ClientTooltipComponent, TooltipComponent {
    private final PatternEncodingPreview preview;

    public PatternEncodingPreviewTooltip(PatternEncodingPreview preview) {
        this.preview = preview;
    }

    @Override
    public int getHeight() {
        return PatternEncodingPreviewRenderer.height(preview) + 4;
    }

    @Override
    public int getWidth(Font font) {
        return PatternEncodingPreviewRenderer.width();
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        PatternEncodingPreviewRenderer.render(graphics, font, x, y + 2, preview);
    }
}
