package com.lhy.ae2utility.jei;

import com.lhy.ae2utility.compat.ModCapabilities;
import com.lhy.ae2utility.util.WeakValueRegistry;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.lhy.ae2utility.compat.JeictCompat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import appeng.client.gui.Icon;

import com.lhy.ae2utility.client.Ae2UtilityClientConfig;
import com.lhy.ae2utility.client.PatternEncodingPreviewTooltip;
import com.lhy.ae2utility.network.EncodePreviewResultPacket;
import com.lhy.ae2utility.util.PatternEncodingPreview;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.ingredients.ITypedIngredient;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import mezz.jei.api.gui.drawable.IDrawable;

import mezz.jei.common.util.ImmutableRect2i;

public class EncodePatternButtonController implements IIconButtonController {
    private static final WeakValueRegistry<IRecipeLayoutDrawable<?>, EncodePatternButtonController> CONTROLLERS = new WeakValueRegistry<>();

    private static long previewGeneration;
    private long observedPreviewGeneration = previewGeneration;
    private final IRecipeLayoutDrawable<?> recipeLayout;

    /** Called by the existing client lifecycle owner; keeps the registry non-owning. */
    public static void invalidateAllPreviews() {
        previewGeneration++;
    }

    private com.lhy.ae2utility.client.jei.EncodePatternViewModel snapshot() {
        return observedPreviewGeneration == previewGeneration ? query.snapshot()
                : com.lhy.ae2utility.client.jei.EncodePatternViewModel.EMPTY;
    }
    private boolean isAvailable = false;
    private boolean hasBlankPattern = false;

    private final com.lhy.ae2utility.client.jei.EncodePatternQueryState query;

    private static final int AE_BLUE_SLOT_HIGHLIGHT_COLOR = 0x400000FF;
    private static final int AE_RED_SLOT_HIGHLIGHT_COLOR = 0x66FF0000;
    /**
     * 由 JEI 官方 {@link #drawExtras} 回调在按钮真正被绘制时刷新。
     * 用于在不依赖 mixin 回读 IconButton 的前提下，判断这条 recipe 当前页是否可见。
     */
    private ImmutableRect2i encodeButtonScreenBounds = ImmutableRect2i.EMPTY;
    private int visibleButtonGraceTicks = 0;

    private final IDrawable ARROW_ICON = new IDrawable() {
        @Override
        public int getWidth() { return 10; }
        @Override
        public int getHeight() { return 10; }
        @Override
        public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
            // 注意：JEI 在调用此方法前已经通过 poseStack.translate 移动了坐标，
            // 所以 xOffset/yOffset 始终为 0。背景色填充使用相对坐标即可。
            // 还原为半透明颜色
            // 与 Ae2TerminalRecipeTransferHandler 同款：缺料橙 / 可合成蓝；无 ME 快照时回退 CraftableStateCache
            if (snapshot().backdropColor() != 0) {
                guiGraphics.fill(xOffset - 1, yOffset - 1, xOffset + 11, yOffset + 11, snapshot().backdropColor());
            }

            Icon.ARROW_UP.getBlitter().dest(xOffset, yOffset, 10, 10).blit(guiGraphics);
        }
    };

    public EncodePatternButtonController(IRecipeLayoutDrawable<?> recipeLayout) {
        this.recipeLayout = recipeLayout;
        this.query = new com.lhy.ae2utility.client.jei.EncodePatternQueryState(recipeLayout);
        CONTROLLERS.put(recipeLayout, this);
    }

    public boolean isMouseOverEncodeButton(double mouseX, double mouseY) {
        if (!isAvailable || encodeButtonScreenBounds.isEmpty()) {
            return false;
        }
        return encodeButtonScreenBounds.contains(mouseX, mouseY);
    }

    /**
     * Mixin 热路径：未悬停或服务端尚未回传时立即返回 {@code null}。
     */
    public static @Nullable ITypedIngredient<?> peekPinnedDisplayedIngredient(
            @Nullable IRecipeLayoutDrawable<?> layout, IRecipeSlotView slot) {
        if (layout == null || slot == null || !Ae2UtilityClientConfig.previewEncodeIngredientsOnArrowHover()) {
            return null;
        }
        EncodePatternButtonController controller = CONTROLLERS.get(layout);
        return controller == null ? null : controller.pinnedDisplayedIngredient(slot);
    }

    private @Nullable ITypedIngredient<?> pinnedDisplayedIngredient(IRecipeSlotView slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || !isMouseOverEncodeButtonNow(mc)) {
            return null;
        }
        return snapshot().chosenIngredients().get(slot);
    }

    private boolean isMouseOverEncodeButtonNow(Minecraft mc) {
        var win = mc.getWindow();
        double mouseX = mc.mouseHandler.xpos() * win.getGuiScaledWidth() / Math.max(1, win.getScreenWidth());
        double mouseY = mc.mouseHandler.ypos() * win.getGuiScaledHeight() / Math.max(1, win.getScreenHeight());
        return isMouseOverEncodeButton(mouseX, mouseY);
    }

    @Override
    public void initState(IButtonState state) {
        state.setIcon(ARROW_ICON);
    }

    @Override
    public void updateState(IButtonState state) {
        if (observedPreviewGeneration != previewGeneration) {
            query.invalidate();
            observedPreviewGeneration = previewGeneration;
        }
        Minecraft mc = Minecraft.getInstance();
        isAvailable = mc.player != null && playerMayEncodePatterns(mc.player);
        hasBlankPattern = isAvailable;
        state.setVisible(isAvailable);
        state.setActive(isAvailable);
        if (visibleButtonGraceTicks > 0) {
            visibleButtonGraceTicks--;
        } else {
            encodeButtonScreenBounds = ImmutableRect2i.EMPTY;
        }
        query.prepare(isAvailable, visibleButtonGraceTicks > 0,
                mc.getWindow() != null && isMouseOverEncodeButtonNow(mc));
    }

    public static boolean playerMayEncodePatterns(LocalPlayer player) {
        return com.lhy.ae2utility.client.PatternEncodingAvailability.playerMayEncodePatterns(player);
    }

    @Override
    public void drawExtras(GuiGraphics guiGraphics, Rect2i buttonArea, int mouseX, int mouseY, float partialTick) {
        encodeButtonScreenBounds = new ImmutableRect2i(
                buttonArea.getX(), buttonArea.getY(), buttonArea.getWidth(), buttonArea.getHeight());
        visibleButtonGraceTicks = 2;
        if (!isAvailable || !isMouseOverEncodeButton(mouseX, mouseY)) {
            return;
        }
        Rect2i recipeRect = recipeLayout.getRect();
        var poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(recipeRect.getX(), recipeRect.getY(), 0);
        drawSlotHighlights(guiGraphics, mouseX, mouseY);
        poseStack.popPose();
    }

    public void drawSlotHighlights(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        var snapshot = snapshot();
        for (IRecipeSlotView slot : snapshot.inputSlots()) {
            if (Boolean.TRUE.equals(snapshot.craftableInputs().get(slot))) {
                slot.drawHighlight(guiGraphics, AE_BLUE_SLOT_HIGHLIGHT_COLOR);
            } else if (!slot.isEmpty()) {
                slot.drawHighlight(guiGraphics, AE_RED_SLOT_HIGHLIGHT_COLOR);
            }
        }
    }

    public static void handlePreviewResult(EncodePreviewResultPacket payload) {
        if (payload == null) {
            return;
        }
        EncodePatternButtonController controller = CONTROLLERS.find(value -> value.observedPreviewGeneration == previewGeneration
                && value.query.accepts(payload.requestId()));
        if (controller != null) {
            controller.query.applyServerEncodedInputs(payload);
        }
    }

    @Override
    public boolean onPress(IJeiUserInput input) {
        return isAvailable && hasBlankPattern
                && com.lhy.ae2utility.client.jei.EncodePatternActions.dispatch(recipeLayout, input);
    }

    @Override
    public void getTooltips(ITooltipBuilder tooltip) {
        boolean detailExpanded = ae2utility$isDetailTooltipExpanded();
        tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_button"));
        if (isAvailable) {
            tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_blue_slots").withStyle(ChatFormatting.BLUE));
            if (Ae2UtilityClientConfig.previewEncodeIngredientsOnArrowHover()) {
                tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_hover_preview")
                        .withStyle(ChatFormatting.GRAY));
            }
            if (Ae2UtilityClientConfig.previewEncodeTerminalOnArrowHover()) {
                PatternEncodingPreview preview = snapshot().terminalPreview();
                if (preview != null) {
                    tooltip.add(new PatternEncodingPreviewTooltip(preview));
                }
            }
            if (JeictCompat.isLoaded()) {
                tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_alt_jeict").withStyle(ChatFormatting.WHITE));
            }
            if (!detailExpanded) {
                tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_details_prefix")
                        .withStyle(ChatFormatting.WHITE)
                        .append(Component.translatable("jei.tooltip.ae2utility.encode_pattern_details_key")
                                .withStyle(ChatFormatting.AQUA))
                        .append(Component.translatable("jei.tooltip.ae2utility.encode_pattern_details_suffix")
                                .withStyle(ChatFormatting.WHITE)));
            }
        }

        if (isAvailable && detailExpanded) {
            tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_ctrl_left_hint").withStyle(ChatFormatting.WHITE));
        }

        if (isAvailable && detailExpanded && ModCapabilities.hasExtendedAePlus()) {
            tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_shift").withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_ctrl_shift_hint").withStyle(ChatFormatting.WHITE));
        }
    }

    private static boolean ae2utility$isDetailTooltipExpanded() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() == null) {
            return false;
        }
        long window = minecraft.getWindow().getWindow();
        return Screen.hasShiftDown() && InputConstants.isKeyDown(window, GLFW.GLFW_KEY_N);
    }
}
