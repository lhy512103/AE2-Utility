package com.lhy.ae2utility.emi;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.Nullable;

import com.lhy.ae2utility.client.Ae2UtilityClientConfig;
import com.lhy.ae2utility.client.EncodePreviewClientDispatch;
import com.lhy.ae2utility.client.PatternEncodingPreviewTooltip;
import com.lhy.ae2utility.jei.CraftableStateCache;
import com.lhy.ae2utility.jei.EncodePatternButtonController;
import com.lhy.ae2utility.network.EncodePreviewResultPacket;
import com.lhy.ae2utility.network.QueryEncodePreviewPacket;
import com.lhy.ae2utility.util.PatternEncodingPreview;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import dev.emi.emi.screen.WidgetGroup;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Standalone EMI recipe action button. Left-click encodes, Shift+left-click uploads.
 *
 * <p>Reuses EMI's own button artwork: the blank beveled button slot in
 * {@code emi:textures/gui/buttons.png} (column at u=72), then overlays the AE2
 * up-arrow icon on top so it visually matches the native EMI recipe buttons.</p>
 */
public class EmiEncodePatternButtonWidget extends Widget {
    private static final int SIZE = 12;
    private static final ResourceLocation EMI_BUTTONS =
            ResourceLocation.fromNamespaceAndPath("emi", "textures/gui/buttons.png");
    /** u offset of the blank button slot in buttons.png. */
    private static final int BLANK_BUTTON_U = 72;
    private static final int AE_BLUE_BUTTON_HIGHLIGHT_COLOR = 0x804545FF;
    private static final int AE_ORANGE_BUTTON_HIGHLIGHT_COLOR = 0x80FFA500;
    private static final int AE_BLUE_SLOT_HIGHLIGHT_COLOR = 0x400000FF;
    private static final int AE_RED_SLOT_HIGHLIGHT_COLOR = 0x66FF0000;
    private static final AtomicInteger NEXT_PREVIEW_REQUEST_ID = new AtomicInteger(1);
    private static final WeakHashMap<EmiEncodePatternButtonWidget, Boolean> PREVIEW_WIDGETS = new WeakHashMap<>();

    static {
        EncodePreviewClientDispatch.addListener(EmiEncodePatternButtonWidget::handlePreviewResult);
    }

    private final int x;
    private final int y;
    private final EmiRecipe recipe;
    private final WidgetGroup group;
    private final int previewRequestId = NEXT_PREVIEW_REQUEST_ID.getAndIncrement();
    private boolean previewRequestInFlight;
    private boolean previewRequestFailed;
    private @Nullable PatternEncodingPreview cachedTerminalPreview;
    private @Nullable PatternEncodingPreview cachedLocalTerminalPreview;

    public EmiEncodePatternButtonWidget(int x, int y, EmiRecipe recipe, WidgetGroup group) {
        this.x = x;
        this.y = y;
        this.recipe = recipe;
        this.group = group;
        PREVIEW_WIDGETS.put(this, Boolean.TRUE);
    }

    @Override
    public Bounds getBounds() {
        return new Bounds(x, y, SIZE, SIZE);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        boolean hovered = getBounds().contains(mouseX, mouseY);
        int v = hovered ? SIZE : 0;
        graphics.blit(EMI_BUTTONS, x, y, SIZE, SIZE, (float) BLANK_BUTTON_U, (float) v, SIZE, SIZE, 256, 256);

        int backdrop = computeBackdropColor();
        if (backdrop != 0) {
            graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, backdrop);
        }
        Icon.ARROW_UP.getBlitter().dest(x + 1, y + 1, 10, 10).blit(graphics);

        if (hovered && available()) {
            drawSlotHighlights(graphics);
            if (Ae2UtilityClientConfig.previewEncodeTerminalOnArrowHover()) {
                ensureServerEncodedPreview();
            }
        }
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(int mouseX, int mouseY) {
        List<ClientTooltipComponent> tooltip = new ArrayList<>();
        tooltip.add(text(Component.translatable("emi.tooltip.ae2utility.encode_pattern_button")));
        tooltip.add(text(Component.translatable("emi.tooltip.ae2utility.encode_pattern_click")
                .withStyle(ChatFormatting.WHITE)));
        tooltip.add(text(Component.translatable("emi.tooltip.ae2utility.encode_pattern_shift")
                .withStyle(ChatFormatting.WHITE)));
        if (available()) {
            tooltip.add(text(Component.translatable("emi.tooltip.ae2utility.encode_pattern_blue_slots")
                    .withStyle(ChatFormatting.BLUE)));
            if (Ae2UtilityClientConfig.previewEncodeTerminalOnArrowHover()) {
                ensureServerEncodedPreview();
                PatternEncodingPreview preview = terminalPreviewForTooltip();
                if (preview != null) {
                    tooltip.add(new PatternEncodingPreviewTooltip(preview));
                }
            }
        }
        return tooltip;
    }

    private static ClientTooltipComponent text(Component component) {
        return ClientTooltipComponent.create(component.getVisualOrderText());
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || !getBounds().contains(mouseX, mouseY)) {
            return false;
        }
        boolean upload = Screen.hasShiftDown();
        EmiEncodePacketFactory.tryCreate(recipe, upload)
                .ifPresent(packet -> PacketDistributor.sendToServer(packet));
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        return true;
    }

    private boolean available() {
        var player = Minecraft.getInstance().player;
        return player != null && EncodePatternButtonController.playerMayEncodePatterns(player);
    }

    /** Highlights each recipe input slot: blue if it has a pattern, red otherwise (matches JEI). */
    private void drawSlotHighlights(GuiGraphics graphics) {
        if (group == null) {
            return;
        }
        for (Widget widget : group.widgets) {
            if (!(widget instanceof SlotWidget slot) || slot.getRecipe() != null) {
                continue; // skip non-slots and logical output slots
            }
            var ingredient = slot.getStack();
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            Bounds b = slot.getBounds();
            int color = EmiEncodePacketFactory.isIngredientCraftable(ingredient)
                    ? AE_BLUE_SLOT_HIGHLIGHT_COLOR
                    : AE_RED_SLOT_HIGHLIGHT_COLOR;
            graphics.fill(b.x(), b.y(), b.x() + b.width(), b.y() + b.height(), color);
        }
    }

    private int computeBackdropColor() {
        if (!available()) {
            return 0;
        }
        var packet = EmiEncodePacketFactory.tryCreate(recipe, false).orElse(null);
        if (packet == null) {
            return 0;
        }
        for (GenericStack output : packet.outputs()) {
            if (output != null && output.what() != null && CraftableStateCache.isCraftable(output.what())) {
                return AE_BLUE_BUTTON_HIGHLIGHT_COLOR;
            }
        }
        for (List<GenericStack> slot : packet.inputs()) {
            if (slot == null) {
                continue;
            }
            for (GenericStack input : slot) {
                if (input != null && input.what() != null && CraftableStateCache.isCraftable(input.what())) {
                    return AE_ORANGE_BUTTON_HIGHLIGHT_COLOR;
                }
            }
        }
        return 0;
    }

    private void ensureServerEncodedPreview() {
        if (cachedTerminalPreview != null || previewRequestInFlight || previewRequestFailed) {
            return;
        }
        EmiEncodePacketFactory.tryCreate(recipe, false).ifPresentOrElse(packet -> {
            previewRequestInFlight = true;
            PacketDistributor.sendToServer(new QueryEncodePreviewPacket(previewRequestId, packet));
        }, () -> previewRequestFailed = true);
    }

    private @Nullable PatternEncodingPreview terminalPreviewForTooltip() {
        if (cachedTerminalPreview != null) {
            return cachedTerminalPreview;
        }
        if (cachedLocalTerminalPreview != null) {
            return cachedLocalTerminalPreview;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        cachedLocalTerminalPreview = EmiEncodePacketFactory.tryCreate(recipe, false)
                .map(packet -> PatternEncodingPreview.guess(packet, mc.level,
                        CraftableStateCache::isCraftable,
                        mc.player == null ? null : mc.player.getInventory().items))
                .orElse(null);
        return cachedLocalTerminalPreview;
    }

    static void handlePreviewResult(EncodePreviewResultPacket payload) {
        if (payload == null) {
            return;
        }
        for (EmiEncodePatternButtonWidget widget : PREVIEW_WIDGETS.keySet()) {
            if (widget.previewRequestId == payload.requestId()) {
                widget.previewRequestInFlight = false;
                widget.cachedTerminalPreview = payload.hasPanel() ? payload.panelPreview() : null;
                widget.cachedLocalTerminalPreview = widget.cachedTerminalPreview;
                if (widget.cachedTerminalPreview == null) {
                    widget.previewRequestFailed = true;
                }
                return;
            }
        }
    }
}
