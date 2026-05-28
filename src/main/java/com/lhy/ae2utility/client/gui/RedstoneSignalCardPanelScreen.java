package com.lhy.ae2utility.client.gui;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.client.gui.Icon;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.TabButton;

import com.lhy.ae2utility.card.RedstoneSignalCardMode;
import com.lhy.ae2utility.init.ModDataComponents;
import com.lhy.ae2utility.init.ModItems;
import com.lhy.ae2utility.item.RedstoneSignalCardItem;
import com.lhy.ae2utility.network.RedstoneSignalCardConfigApplyPacket;

/**
 * 红石发信卡配置：中央半透明遮罩、暂停游戏。
 * AE2 {@link appeng.client.gui.widgets.VerticalButtonBar}：左侧 {@code vertical_buttons_bg}；
 * AE2 {@link appeng.client.gui.widgets.IconButton}：工具栏底图/hover/hover Y+1；模式按钮在主面板矩形之外，与左上角相接。
 */
public class RedstoneSignalCardPanelScreen extends Screen {

    private final InteractionHand hand;
    private RedstoneSignalCardGuiSpecs lay;

    private int panelLeft;
    private int panelTop;

    private int editedTicks;
    private RedstoneSignalCardMode editedMode;
    private int focusedSliderRow = -1;
    private boolean draggingSlider;
    private @Nullable Integer dragSliderRow;
    private @Nullable Integer pinnedSliderRow;

    private int modeIconLogicalScreenX;
    private int modeIconLogicalScreenY;
    private boolean modeButtonHovered;

    private int closeButtonScreenX;
    private int closeButtonScreenY;
    private boolean closeButtonHovered;

    private Rect2i closeButtonBounds = new Rect2i(0, 0, 0, 0);

    public RedstoneSignalCardPanelScreen(InteractionHand hand) {
        super(Component.translatable("gui.ae2utility.redstone_signal_card"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        super.init();
        RedstoneSignalCardGuiSpecs.reloadFromDisk();
        this.lay = RedstoneSignalCardGuiSpecs.getOrLoad();
        this.panelLeft = (this.width - lay.panelWidth) / 2;
        this.panelTop = (this.height - lay.panelHeight) / 2;
        loadFromHeldCard();
        layoutDerivedPositions();
    }

    private void layoutDerivedPositions() {
        closeButtonScreenX = panelLeft + lay.closeButtonOffsetXFromPanelLeft;
        closeButtonScreenY = panelTop + lay.closeButtonOffsetYFromPanelTop;
        closeButtonBounds = new Rect2i(closeButtonScreenX, closeButtonScreenY, lay.closeButtonW, lay.closeButtonH);

        modeIconLogicalScreenX = panelLeft + lay.modeCycleOffsetXFromPanelLeft;
        modeIconLogicalScreenY = panelTop + lay.modeCycleOffsetYFromPanelTop;
    }

    private void loadFromHeldCard() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ItemStack stack = mc.player.getItemInHand(hand);
        if (!stack.is(ModItems.REDSTONE_SIGNAL_CARD.get())) {
            return;
        }
        int t = stack.getOrDefault(ModDataComponents.REDSTONE_SIGNAL_HOLD_TICKS, RedstoneSignalCardItem.defaultHoldTicks());
        this.editedTicks = RedstoneSignalCardBrassTicks.clampTotal(t);
        this.editedMode = stack.getOrDefault(ModDataComponents.REDSTONE_SIGNAL_CARD_MODE, RedstoneSignalCardMode.ORDER);
        this.pinnedSliderRow = this.editedTicks <= RedstoneSignalCardBrassTicks.MIN_TICKS ? 0
                : RedstoneSignalCardBrassTicks.canonicalRow(this.editedTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int a = 0xC0;
        graphics.fillGradient(0, 0, this.width, this.height, 0x101010 | (a << 24), 0x101010 | (a << 24));
    }

    /** 与 AE2 {@code IconButton#renderWidget} 一致的非半尺寸分支。logicalX/Y 为 16×16 图标左上角；背景在其左移 1px。 */
    private static void drawAeFullSizeIconButton(GuiGraphics g, Icon icon, int logicalX, int logicalY, boolean hovered) {
        int yOffset = hovered ? 1 : 0;
        Icon bg = hovered ? Icon.TOOLBAR_BUTTON_BACKGROUND_HOVER : Icon.TOOLBAR_BUTTON_BACKGROUND;
        bg.getBlitter()
                .dest(logicalX - 1, logicalY + yOffset, 18, 20)
                .blit(g);
        icon.getBlitter().dest(logicalX, logicalY + 1 + yOffset).blit(g);
    }

    /**
     * 与 AE2 {@code textures/gui/sprites/vertical_buttons_bg.png.mcmeta}（九切片 top=2 bottom=4）一致，用
     * {@link Blitter#texture} 直接绑 PNG，采样链路与 {@link Icon} 相同；避免 {@link GuiGraphics#blitSprite} 的 GUI 精灵 atlas。
     */
    private static final int AE_VERTICAL_SIDEBAR_TEX_W = 21;
    private static final int AE_VERTICAL_SIDEBAR_TEX_H = 26;
    private static final int AE_VERTICAL_SIDEBAR_SLICE_TOP = 2;
    private static final int AE_VERTICAL_SIDEBAR_SLICE_BOTTOM = 4;

    /**
     * 与 AE2 {@link appeng.client.gui.widgets.VerticalButtonBar#drawBackgroundLayer} 占位一致：<br/>
     * 逻辑的侧栏占位右上角对齐 {@code (panelLeft + rightOverlap, panelTop + topOffset)}。
     */
    private static void renderAeVerticalButtonsStripBackground(GuiGraphics g, RedstoneSignalCardGuiSpecs lay,
            int panelLeft, int panelTop) {
        int w = lay.sidebarOuterWidthPx;
        int h = lay.sidebarOuterHeightPx;
        int logicalLeft = panelLeft - w + lay.sidebarRightOverlapPanelLeftPx;
        int logicalTop = panelTop + lay.sidebarTopOffsetFromPanelTopPx;
        int bx = logicalLeft - 2;
        int by = logicalTop - 1;
        int destW = w + 1;
        int destH = h + 4;

        int tw = AE_VERTICAL_SIDEBAR_TEX_W;
        int th = AE_VERTICAL_SIDEBAR_TEX_H;
        int sliceTop = AE_VERTICAL_SIDEBAR_SLICE_TOP;
        int sliceBot = AE_VERTICAL_SIDEBAR_SLICE_BOTTOM;
        int midSrcH = th - sliceTop - sliceBot;
        int midDestH = destH - sliceTop - sliceBot;
        Blitter.texture(lay.sidebarBackgroundTexture, tw, th).src(0, 0, tw, sliceTop).dest(bx, by, destW, sliceTop)
                .blit(g);
        Blitter.texture(lay.sidebarBackgroundTexture, tw, th).src(0, sliceTop, tw, midSrcH)
                .dest(bx, by + sliceTop, destW, midDestH).blit(g);
        Blitter.texture(lay.sidebarBackgroundTexture, tw, th).src(0, th - sliceBot, tw, sliceBot)
                .dest(bx, by + sliceTop + midDestH, destW,
                sliceBot).blit(g);
    }

    private Icon iconForCurrentMode() {
        return switch (editedMode) {
            case ORDER -> Icon.REDSTONE_ON;
            case CRAFT -> Icon.REDSTONE_OFF;
            case UNTIL_RECIPE_COMPLETE -> Icon.REDSTONE_PULSE;
        };
    }

    private int currentModeColor() {
        return switch (editedMode) {
            case ORDER -> 0xFF55AA55;
            case CRAFT -> 0xFF4A78D0;
            case UNTIL_RECIPE_COMPLETE -> 0xFFD04A4A;
        };
    }

    private int currentIntervalColor() {
        return 0xFFD04A4A;
    }

    private boolean shouldShowIntervalControls() {
        return editedMode != RedstoneSignalCardMode.UNTIL_RECIPE_COMPLETE;
    }

    private String currentIntervalText() {
        if (editedTicks <= 60) {
            return editedTicks + "tick";
        }
        if (editedTicks <= 60 * 20) {
            return (editedTicks / 20) + "秒";
        }
        return (editedTicks / (60 * 20)) + "分";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        RenderSystem.enableBlend();
        modeButtonHovered = insideModeCycleHitbox(mouseX, mouseY);
        closeButtonHovered =
                inside(mouseX, mouseY, closeButtonBounds.getX(), closeButtonBounds.getY(), closeButtonBounds.getWidth(),
                        closeButtonBounds.getHeight());

        graphics.blit(lay.panelTexture,
                panelLeft,
                panelTop,
                lay.panelBgU,
                lay.panelBgV,
                lay.panelWidth,
                lay.panelHeight,
                lay.texW,
                lay.texH);

        renderAeVerticalButtonsStripBackground(graphics, lay, panelLeft, panelTop);

        Minecraft mc = Minecraft.getInstance();

        Icon modeIcon = iconForCurrentMode();
        drawAeFullSizeIconButton(graphics, modeIcon, modeIconLogicalScreenX, modeIconLogicalScreenY, modeButtonHovered);

        Component title = Component.translatable(lay.titleLangKey);
        graphics.drawString(font, title, panelLeft + lay.titleX, panelTop + lay.titleY, lay.titleTextColorArgb, false);

        int modeY = panelTop + lay.titleY + font.lineHeight + lay.modeYOffsetBelowTitleBaselinePx;
        Component modePrefix = Component.translatable("gui.ae2utility.redstone_signal_card.current_mode");
        graphics.drawString(font, modePrefix, panelLeft + lay.titleX, modeY, lay.titleTextColorArgb, false);
        int modeNameX = panelLeft + lay.titleX + font.width(modePrefix);
        graphics.drawString(font, editedMode.getDisplayName(), modeNameX, modeY, currentModeColor(), false);
        if (shouldShowIntervalControls()) {
            Component intervalPrefix = Component.translatable("gui.ae2utility.redstone_signal_card.current_interval_prefix");
            int intervalPrefixX = modeNameX + font.width(editedMode.getDisplayName()) + 2;
            graphics.drawString(font, intervalPrefix, intervalPrefixX, modeY, lay.titleTextColorArgb, false);
            int intervalValueX = intervalPrefixX + font.width(intervalPrefix);
            String intervalText = currentIntervalText();
            graphics.drawString(font, intervalText, intervalValueX, modeY, currentIntervalColor(), false);
            graphics.drawString(font, Component.translatable("gui.ae2utility.redstone_signal_card.current_interval_suffix"),
                    intervalValueX + font.width(intervalText), modeY, lay.titleTextColorArgb, false);
        }

        int hintIdx = editedMode.ordinal();
        if (hintIdx >= 0 && hintIdx < lay.hintLangKeys.length) {
            Component hint = Component.translatable(lay.hintLangKeys[hintIdx]);
            int hy = modeY + font.lineHeight + lay.hintYOffsetBelowModeBaselinePx;
            var lines = font.split(hint, lay.hintMaxWidth);
            for (int i = 0; i < lines.size(); i++) {
                graphics.drawString(font, lines.get(i), panelLeft + lay.titleX, hy + i * font.lineHeight,
                        lay.hintTextColorsArgb[hintIdx], false);
            }
        }

        for (int r = 0; r < 3; r++) {
            int labelY = panelTop + switch (r) {
                case 0 -> lay.tickLabelY;
                case 1 -> lay.secondsLabelY;
                default -> lay.minutesLabelY;
            };
            Component lab = Component.translatable(switch (r) {
                case 0 -> "gui.ae2utility.redstone_signal_card.unit.tick";
                case 1 -> "gui.ae2utility.redstone_signal_card.unit.seconds";
                default -> "gui.ae2utility.redstone_signal_card.unit.minutes";
            });
            graphics.drawString(font, lab, panelLeft + lay.tickLabelX, labelY, lay.titleTextColorArgb, false);
        }

        drawCloseButton(graphics);

        if (shouldShowIntervalControls()) {
            if (editedTicks <= RedstoneSignalCardBrassTicks.MIN_TICKS) {
                drawKnob(graphics, 0, 0, false);
            } else {
                int knobRow = activeSliderDisplayRow();
                int col = RedstoneSignalCardBrassTicks.sliderColumnForRow(editedTicks, knobRow);
                boolean knobActive = draggingSlider || insideKnob(mouseX, mouseY, knobRow, col);
                drawKnob(graphics, knobRow, col, knobActive);
            }
        } else {
            drawKnob(graphics, 0, 0, false);
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        List<Component> tip = tooltipAt(mouseX, mouseY);
        if (tip != null && mc.font != null) {
            graphics.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    /**
     * 界面里始终只显示一个手柄。
     * 悬停/拖动到某一行时临时显示在该行；否则显示在当前 tick 值所属的规范行。
     */
    private int activeSliderDisplayRow() {
        if (editedTicks <= RedstoneSignalCardBrassTicks.MIN_TICKS) {
            return 0;
        }
        if (draggingSlider && dragSliderRow != null) {
            return dragSliderRow;
        }
        if (pinnedSliderRow != null) {
            return pinnedSliderRow;
        }
        return RedstoneSignalCardBrassTicks.canonicalRow(editedTicks);
    }

    private void drawCloseButton(GuiGraphics graphics) {
        Icon backdrop = closeButtonHovered ? Icon.TAB_BUTTON_BACKGROUND_FOCUS : Icon.TAB_BUTTON_BACKGROUND;
        backdrop.getBlitter().zOffset(200)
                .dest(closeButtonScreenX, closeButtonScreenY, lay.closeButtonW, lay.closeButtonH)
                .blit(graphics);
        Icon.BACK.getBlitter()
                .zOffset(201)
                .dest(closeButtonScreenX + lay.closeIconOffsetX, closeButtonScreenY + lay.closeIconOffsetY, 16, 16)
                .blit(graphics);
    }

    private boolean insideModeCycleHitbox(int mx, int my) {
        return inside(mx,
                my,
                modeIconLogicalScreenX - 1,
                modeIconLogicalScreenY,
                18,
                20);
    }

    private void drawKnob(GuiGraphics graphics, int row, int column, boolean rowActive) {
        Rect2i knobBounds = knobBoundsFor(row, column);
        int kx = knobBounds.getX();
        int ky = knobBounds.getY();
        int u = rowActive ? lay.knobUvActiveU : lay.knobUvInactiveU;
        int v = rowActive ? lay.knobUvActiveV : lay.knobUvInactiveV;
        graphics.blit(lay.panelTexture, kx, ky, u, v, lay.knobW, lay.knobH, lay.texW, lay.texH);
    }

    private Rect2i knobBoundsFor(int row, int column) {
        int cy = panelTop + lay.sliderCenterYFromPanelTop[row];
        int trackLeft = panelLeft + lay.sliderLeftX;
        double frac = column / (double) lay.maxColumnInclusive;
        int knobCx = trackLeft + (int) Math.round(frac * lay.trackLengthPx);
        int kx = knobCx - lay.knobW / 2;
        int ky = cy - lay.knobH / 2;
        return new Rect2i(kx, ky, lay.knobW, lay.knobH);
    }

    private boolean insideKnob(double mouseX, double mouseY, int row, int column) {
        Rect2i bounds = knobBoundsFor(row, column);
        return mouseX >= bounds.getX() && mouseX < bounds.getX() + bounds.getWidth()
                && mouseY >= bounds.getY() && mouseY < bounds.getY() + bounds.getHeight();
    }

    private int rowAtMouse(double mx, double my) {
        for (int r = 0; r < 3; r++) {
            int cy = panelTop + lay.sliderCenterYFromPanelTop[r];
            if (my >= cy - lay.trackHitHalfHeight && my <= cy + lay.trackHitHalfHeight
                    && mx >= panelLeft + lay.sliderLeftX - 4
                    && mx <= panelLeft + lay.sliderLeftX + lay.trackLengthPx + 4) {
                return r;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if (inside(mx, my, closeButtonBounds.getX(), closeButtonBounds.getY(), closeButtonBounds.getWidth(),
                closeButtonBounds.getHeight())) {
            playButtonClickSound();
            saveAndClose();
            return true;
        }

        if (insideModeCycleHitbox(mx, my)) {
            var vals = RedstoneSignalCardMode.values();
            int o = editedMode.ordinal();
            if (button == 0) {
                editedMode = vals[(o + 1) % vals.length];
            } else if (button == 1) {
                editedMode = vals[(o - 1 + vals.length) % vals.length];
            }
            if (button == 0 || button == 1) {
                if (!shouldShowIntervalControls()) {
                    draggingSlider = false;
                    dragSliderRow = null;
                }
                playButtonClickSound();
            }
            return button == 0 || button == 1;
        }

        if (!shouldShowIntervalControls()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int knobRow = activeSliderDisplayRow();
        int knobCol = editedTicks <= RedstoneSignalCardBrassTicks.MIN_TICKS ? 0
                : RedstoneSignalCardBrassTicks.sliderColumnForRow(editedTicks, knobRow);
        if (insideKnob(mx, my, knobRow, knobCol)) {
            draggingSlider = true;
            dragSliderRow = knobRow;
            pinnedSliderRow = knobRow;
            focusedSliderRow = knobRow;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggingSlider = false;
            dragSliderRow = null;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!shouldShowIntervalControls()) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        if (button == 0 && draggingSlider) {
            int hoveredRow = rowAtMouse(mouseX, mouseY);
            int row = hoveredRow >= 0 ? hoveredRow : dragSliderRow != null ? dragSliderRow : -1;
            if (row >= 0) {
                dragSliderRow = row;
                applyColumnFromMouse((int) mouseX, row, Screen.hasShiftDown());
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!shouldShowIntervalControls()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int row = rowAtMouse(mouseX, mouseY);
        if (row < 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int col = RedstoneSignalCardBrassTicks.sliderColumnForRow(editedTicks, row);
        int step = Screen.hasShiftDown() ? lay.milestoneStep : 1;
        int delta = -(int) Math.signum(scrollY) * step;
            int nc =
                Math.min(lay.maxColumnInclusive, Math.max(0, col + delta));
        editedTicks = RedstoneSignalCardBrassTicks.encode(row, nc);
        pinnedSliderRow = row;
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (!draggingSlider) {
            focusedSliderRow = rowAtMouse(mouseX, mouseY);
        }
        super.mouseMoved(mouseX, mouseY);
    }

    private void applyColumnFromMouse(int mouseX, int row, boolean shift) {
        int trackLeft = panelLeft + lay.sliderLeftX;
        int col = RedstoneSignalCardBrassTicks.columnFromMouseX(mouseX, trackLeft, lay.trackLengthPx,
                lay.maxColumnInclusive,
                shift);
        editedTicks = RedstoneSignalCardBrassTicks.encode(row, col);
        pinnedSliderRow = row;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void playButtonClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void saveAndClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getItemInHand(hand).getItem() instanceof RedstoneSignalCardItem) {
            boolean main = hand == InteractionHand.MAIN_HAND;
            PacketDistributor.sendToServer(new RedstoneSignalCardConfigApplyPacket(main,
                    editedMode.ordinal(),
                    editedTicks));
        }
        this.onClose();
    }

    private @Nullable List<Component> tooltipAt(int mx, int my) {
        if (inside(mx, my, closeButtonBounds.getX(), closeButtonBounds.getY(), closeButtonBounds.getWidth(),
                closeButtonBounds.getHeight())) {
            return List.of(Component.translatable("gui.back"));
        }
        if (insideModeCycleHitbox(mx, my)) {
            return List.of(Component.translatable(
                    "gui.ae2utility.redstone_signal_card.mode_btn." + editedMode.getSerializedName()));
        }
        return null;
    }

    public static void open(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new RedstoneSignalCardPanelScreen(hand));
    }
}
