package com.lhy.ae2utility.client;

import org.jetbrains.annotations.Nullable;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.client.gui.style.Blitter;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import com.lhy.ae2utility.util.PatternEncodingPreview;

public final class PatternEncodingPreviewRenderer {
    public static final int PANEL_WIDTH = 124;
    public static final int PANEL_HEIGHT = 66;
    private static final int EXTRA_LINE_HEIGHT = 10;
    private static final ResourceLocation DISABLED_SCROLLER =
            ResourceLocation.fromNamespaceAndPath("ae2", "small_scroller_disabled");

    private PatternEncodingPreviewRenderer() {
    }

    public static int width() {
        return PANEL_WIDTH;
    }

    public static int height(PatternEncodingPreview preview) {
        return PANEL_HEIGHT + (hasOverflow(preview) ? EXTRA_LINE_HEIGHT : 0);
    }

    public static void render(GuiGraphics graphics, Font font, int x, int y, PatternEncodingPreview preview) {
        switch (preview.mode()) {
            case CRAFTING -> renderCrafting(graphics, font, x, y, preview);
            case PROCESSING -> renderProcessing(graphics, font, x, y, preview);
            case SMITHING_TABLE -> renderSmithing(graphics, font, x, y, preview);
            case STONECUTTING -> renderStonecutting(graphics, font, x, y, preview);
        }
        if (hasOverflow(preview)) {
            graphics.drawString(font, overflowText(preview), x, y + PANEL_HEIGHT + 1, 0xFFAAAAAA, false);
        }
    }

    private static void renderCrafting(GuiGraphics graphics, Font font, int x, int y,
            PatternEncodingPreview preview) {
        blitPanel(graphics, x, y, 0, 0);
        for (int slot = 0; slot < 9; slot++) {
            renderSlot(graphics, font, preview.inputs().get(slot), x + 7 + (slot % 3) * 18,
                    y + 7 + (slot / 3) * 18);
        }
        renderSlot(graphics, font, first(preview.outputs()), x + 98, y + 25);
        Icon.S_CLEAR.getBlitter().dest(x + 62, y + 7).blit(graphics);
        (preview.substituteItems() ? Icon.S_SUBSTITUTION_ENABLED : Icon.S_SUBSTITUTION_DISABLED)
                .getBlitter().dest(x + 72, y + 7).blit(graphics);
        (preview.substituteFluids() ? Icon.S_FLUID_SUBSTITUTION_ENABLED : Icon.S_FLUID_SUBSTITUTION_DISABLED)
                .getBlitter().dest(x + 82, y + 7).blit(graphics);
    }

    private static void renderProcessing(GuiGraphics graphics, Font font, int x, int y,
            PatternEncodingPreview preview) {
        blitPanel(graphics, x, y, 0, 70);
        for (int slot = 0; slot < PatternEncodingPreview.VISIBLE_INPUTS; slot++) {
            renderSlot(graphics, font, preview.inputs().get(slot), x + 16 + (slot % 3) * 18,
                    y + 7 + (slot / 3) * 18);
        }
        for (int slot = 0; slot < PatternEncodingPreview.VISIBLE_OUTPUTS; slot++) {
            renderSlot(graphics, font, preview.outputs().get(slot), x + 101, y + 7 + slot * 18);
        }
        Icon.S_CLEAR.getBlitter().dest(x + 71, y + 7).blit(graphics);
        Icon.S_CYCLE.getBlitter().dest(x + 90, y + 7).blit(graphics);
        graphics.blitSprite(DISABLED_SCROLLER, x + 7, y + 7, 7, 15);
    }

    private static void renderSmithing(GuiGraphics graphics, Font font, int x, int y,
            PatternEncodingPreview preview) {
        blitPanel(graphics, x, y, 128, 70);
        for (int slot = 0; slot < 3; slot++) {
            renderSlot(graphics, font, preview.inputs().get(slot), x + 7 + slot * 18, y + 25);
        }
        renderSlot(graphics, font, first(preview.outputs()), x + 101, y + 25);
        Icon.S_CLEAR.getBlitter().dest(x + 6, y + 14).blit(graphics);
        (preview.substituteItems() ? Icon.S_SUBSTITUTION_ENABLED : Icon.S_SUBSTITUTION_DISABLED)
                .getBlitter().dest(x + 16, y + 14).blit(graphics);
    }

    private static void renderStonecutting(GuiGraphics graphics, Font font, int x, int y,
            PatternEncodingPreview preview) {
        blitPanel(graphics, x, y, 0, 140);
        renderSlot(graphics, font, first(preview.inputs()), x + 7, y + 25);
        Blitter.texture("guis/pattern_modes.png").src(124, 162, 20, 22).dest(x + 27, y + 12).blit(graphics);
        renderSlot(graphics, font, first(preview.outputs()), x + 29, y + 15);
        graphics.blitSprite(DISABLED_SCROLLER, x + 109, y + 11, 7, 15);
    }

    private static void blitPanel(GuiGraphics graphics, int x, int y, int srcX, int srcY) {
        Blitter.texture("guis/pattern_modes.png").src(srcX, srcY, PANEL_WIDTH, PANEL_HEIGHT)
                .dest(x, y).blit(graphics);
    }

    private static void renderSlot(GuiGraphics graphics, Font font, @Nullable GenericStack stack, int x, int y) {
        if (stack == null || stack.what() == null) {
            return;
        }
        AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, x, y, stack.what());
        boolean itemUnit = stack.what() instanceof AEItemKey;
        if (!itemUnit || stack.amount() > 1L) {
            StackSizeRenderer.renderSizeLabel(graphics, font, x, y,
                    stack.what().formatAmount(stack.amount(), AmountFormat.SLOT));
        }
    }

    private static @Nullable GenericStack first(java.util.List<GenericStack> stacks) {
        return stacks.isEmpty() ? null : stacks.getFirst();
    }

    private static boolean hasOverflow(PatternEncodingPreview preview) {
        return preview.extraInputs() > 0 || preview.extraOutputs() > 0;
    }

    private static Component overflowText(PatternEncodingPreview preview) {
        int extra = preview.extraInputs() + preview.extraOutputs();
        return Component.translatable("jei.tooltip.ae2utility.encode_pattern_terminal_more", extra);
    }
}
