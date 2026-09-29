package com.lhy.ae2utility.client;

import java.util.List;

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

import com.lhy.ae2utility.util.PatternEncodingPreview;

/**
 * 按 AE2 15 样板编码终端各模式面板（pattern_modes.png，126x68）绘制只读预览。
 * 槽位坐标换算自 screens/terminals/encoding/*.json：面板位于 left 9 / bottom 164。
 */
public final class PatternEncodingPreviewRenderer {
    public static final int PANEL_WIDTH = 126;
    public static final int PANEL_HEIGHT = 68;
    private static final int EXTRA_LINE_HEIGHT = 10;
    private static final Blitter SMALL_SCROLLER_DISABLED =
            Blitter.texture("guis/pattern_modes.png").src(249, 0, 7, 15);

    private PatternEncodingPreviewRenderer() {
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
            graphics.drawString(font, Component.translatable("jei.tooltip.ae2utility.encode_pattern_terminal_more",
                    preview.extraInputs() + preview.extraOutputs()), x, y + PANEL_HEIGHT + 1, 0xFFAAAAAA, false);
        }
    }

    private static void renderCrafting(GuiGraphics graphics, Font font, int x, int y, PatternEncodingPreview preview) {
        blitPanel(graphics, x, y, 0, 0);
        for (int slot = 0; slot < 9; slot++) {
            renderSlot(graphics, font, preview.inputs().get(slot), x + 9 + (slot % 3) * 18, y + 8 + (slot / 3) * 18);
        }
        renderSlot(graphics, font, first(preview.outputs()), x + 101, y + 26);
        halfIcon(graphics, Icon.CLEAR, x + 65, y + 1);
        halfIcon(graphics, preview.substituteItems() ? Icon.SUBSTITUTION_ENABLED : Icon.SUBSTITUTION_DISABLED,
                x + 75, y + 1);
        halfIcon(graphics, preview.substituteFluids() ? Icon.FLUID_SUBSTITUTION_ENABLED
                : Icon.FLUID_SUBSTITUTION_DISABLED, x + 85, y + 1);
    }

    private static void renderProcessing(GuiGraphics graphics, Font font, int x, int y, PatternEncodingPreview preview) {
        blitPanel(graphics, x, y, 0, 70);
        for (int slot = 0; slot < PatternEncodingPreview.VISIBLE_INPUTS; slot++) {
            renderSlot(graphics, font, preview.inputs().get(slot), x + 17 + (slot % 3) * 18, y + 8 + (slot / 3) * 18);
        }
        for (int slot = 0; slot < PatternEncodingPreview.VISIBLE_OUTPUTS; slot++) {
            renderSlot(graphics, font, preview.outputs().get(slot), x + 101, y + 8 + slot * 18);
        }
        halfIcon(graphics, Icon.CLEAR, x + 73, y + 1);
        halfIcon(graphics, Icon.SCHEDULING_DEFAULT, x + 118, y + 12);
        SMALL_SCROLLER_DISABLED.dest(x + 8, y + 8).blit(graphics);
    }

    private static void renderSmithing(GuiGraphics graphics, Font font, int x, int y, PatternEncodingPreview preview) {
        blitPanel(graphics, x, y, 128, 70);
        for (int slot = 0; slot < 3; slot++) {
            renderSlot(graphics, font, preview.inputs().get(slot), x + 12 + slot * 18, y + 26);
        }
        renderSlot(graphics, font, first(preview.outputs()), x + 102, y + 26);
        halfIcon(graphics, Icon.CLEAR, x + 65, y + 1);
        halfIcon(graphics, preview.substituteItems() ? Icon.SUBSTITUTION_ENABLED : Icon.SUBSTITUTION_DISABLED,
                x + 75, y + 1);
    }

    private static void renderStonecutting(GuiGraphics graphics, Font font, int x, int y,
            PatternEncodingPreview preview) {
        blitPanel(graphics, x, y, 0, 141);
        renderSlot(graphics, font, first(preview.inputs()), x + 12, y + 25);
        Blitter.texture("guis/pattern_modes.png").src(126, 159, 16, 18).dest(x + 35, y + 7).blit(graphics);
        renderSlot(graphics, font, first(preview.outputs()), x + 35, y + 8);
        SMALL_SCROLLER_DISABLED.dest(x + 110, y + 7).blit(graphics);
    }

    private static void blitPanel(GuiGraphics graphics, int x, int y, int srcX, int srcY) {
        Blitter.texture("guis/pattern_modes.png").src(srcX, srcY, PANEL_WIDTH, PANEL_HEIGHT).dest(x, y).blit(graphics);
    }

    private static void halfIcon(GuiGraphics graphics, Icon icon, int x, int y) {
        icon.getBlitter().dest(x, y, 8, 8).blit(graphics);
    }

    private static void renderSlot(GuiGraphics graphics, Font font, @Nullable GenericStack stack, int x, int y) {
        if (stack == null || stack.what() == null) {
            return;
        }
        AEKeyRendering.drawInGui(Minecraft.getInstance(), graphics, x, y, stack.what());
        if (!(stack.what() instanceof AEItemKey) || stack.amount() > 1L) {
            StackSizeRenderer.renderSizeLabel(graphics, font, x, y,
                    stack.what().formatAmount(stack.amount(), AmountFormat.SLOT));
        }
    }

    private static @Nullable GenericStack first(List<GenericStack> stacks) {
        return stacks.isEmpty() ? null : stacks.get(0);
    }

    private static boolean hasOverflow(PatternEncodingPreview preview) {
        return preview.extraInputs() > 0 || preview.extraOutputs() > 0;
    }
}
