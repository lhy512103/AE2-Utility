package com.lhy.ae2utility.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;

import com.lhy.ae2utility.client.gui.PatternProviderTearSlotChrome;
import com.lhy.ae2utility.menu.PatternProviderTearSlot;

/**
 * AE2 的 {@link PatternProviderScreen} 未重写 {@code render}，方法在父类 {@link AEBaseScreen} 上，
 * 故将撕裂卡槽位修正与铬框绘制注入 {@code AEBaseScreen}，并对 {@link PatternProviderScreen} 做类型判断。
 * <p>
 * 仅在纯 AE2 环境下才添加撕裂卡槽，本 Mixin 执行右上角定位和自绘铬框。
 */
@Mixin(AEBaseScreen.class)
public abstract class MixinAEBaseScreen {

    private static final int SLOT_SIZE = 18;
    /** 槽右缘距对话框右缘的内边距（像素，负数则伸出右侧）。 */
    private static final int TEAR_SLOT_VANILLA_TOPRIGHT_PAD_RIGHT = -2;
    /** 槽顶缘距对话框顶缘的内边距（像素）。 */
    private static final int TEAR_SLOT_VANILLA_TOPRIGHT_PAD_TOP = 6;
    /** 在右上角锚点之上再平移（右 +X，上 -Y）。 */
    private static final int TEAR_SLOT_VANILLA_NUDGE_X = 17;
    private static final int TEAR_SLOT_VANILLA_NUDGE_Y = -1;

    private static final List<Component> TEAR_SLOT_EMPTY_TOOLTIP = List.of(
            Component.translatable("gui.ae2utility.pattern_provider.nbt_tear_slot.title")
                    .withStyle(ChatFormatting.GRAY));

    /** 首次布局：{@link AEBaseScreen#init} 末尾执行一次。 */
    @Inject(method = "init", at = @At("TAIL"))
    private void ae2utility$positionPatternProviderTearSlot(CallbackInfo ci) {
        ae2utility$repositionPatternProviderTearSlot();
    }

    /**
     * 每帧在升级面板更新槽位坐标之后，再对齐撕裂卡槽（仅无 UpgradesPanel 时生效）。
     * UpgradesPanel 自身在 updateBeforeRender（super.render 内部）里写入 slot.x/y，若有面板则它的值会覆盖我们的，故无需干预。
     */
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    shift = At.Shift.BEFORE))
    private void ae2utility$repositionTearAfterWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if ((Object) this instanceof PatternProviderScreen<?>) {
            ae2utility$repositionPatternProviderTearSlot();
        }
    }

    private void ae2utility$repositionPatternProviderTearSlot() {
        if (!((Object) this instanceof PatternProviderScreen<?> screen)) {
            return;
        }
        PatternProviderMenu menu = screen.getMenu();
        PatternProviderTearSlot tear = null;
        for (Slot s : menu.slots) {
            if (s instanceof PatternProviderTearSlot pts) {
                tear = pts;
                break;
            }
        }
        if (tear == null) {
            return;
        }
        var pos = (SlotMutablePosAccessor) (Slot) tear;
        ae2utility$positionTearDialogTopRightVanilla(screen, pos);
        pos.ae2utility$setX(tear.x + TEAR_SLOT_VANILLA_NUDGE_X);
        pos.ae2utility$setY(tear.y + TEAR_SLOT_VANILLA_NUDGE_Y);
    }

    /** 无升级面板时：固定于对话框内背景右上角。 */
    private void ae2utility$positionTearDialogTopRightVanilla(PatternProviderScreen<?> screen, SlotMutablePosAccessor pos) {
        var inv = (AEBaseScreenInvoker) (Object) this;
        var inner = inv.ae2utility$getBounds(false);
        int w = inner.getWidth();
        int x = w - SLOT_SIZE - TEAR_SLOT_VANILLA_TOPRIGHT_PAD_RIGHT;
        int y = TEAR_SLOT_VANILLA_TOPRIGHT_PAD_TOP;
        pos.ae2utility$setX(Math.max(0, x));
        pos.ae2utility$setY(Math.max(0, y));
    }

    /**
     * 仅在无 UpgradesPanel 时绘制铬框（有面板的模组自行绘制）。
     */
    @Inject(method = "renderBg", at = @At("TAIL"))
    private void ae2utility$drawPatternProviderTearChrome(GuiGraphics graphics, float partialTick, int mouseX, int mouseY,
            CallbackInfo ci) {
        if (!((Object) this instanceof PatternProviderScreen<?> screen)) {
            return;
        }
        PatternProviderMenu menu = screen.getMenu();
        for (Slot slot : menu.slots) {
            if (slot instanceof PatternProviderTearSlot) {
                PatternProviderTearSlotChrome.draw(graphics, screen.getGuiLeft() + slot.x + 2, screen.getGuiTop() + slot.y);
                return;
            }
        }
    }

    /**
     * 在 render 末尾绘制：悬停空槽时的说明。
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void ae2utility$patternProviderTearOverlays(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        if (!((Object) this instanceof PatternProviderScreen<?> screen)) {
            return;
        }
        PatternProviderMenu menu = screen.getMenu();
        PatternProviderTearSlot tear = null;
        for (Slot s : menu.slots) {
            if (s instanceof PatternProviderTearSlot pts) {
                tear = pts;
                break;
            }
        }
        if (tear == null) {
            return;
        }
        if (tear.getItem().isEmpty() && ae2utility$isMouseOverSlot(screen, tear, mouseX, mouseY)) {
            ((AEBaseScreen<?>) (Object) this).drawTooltip(graphics, mouseX, mouseY, TEAR_SLOT_EMPTY_TOOLTIP);
        }
    }

    private static boolean ae2utility$isMouseOverSlot(PatternProviderScreen<?> screen, Slot slot, int mouseX, int mouseY) {
        int x = screen.getGuiLeft() + slot.x + 2;
        int y = screen.getGuiTop() + slot.y;
        return mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
    }

    @Inject(method = "renderSlotHighlight", at = @At("HEAD"), cancellable = true)
    private void ae2utility$offsetTearSlotHoverHighlight(GuiGraphics guiGraphics, Slot slot, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        if (!(slot instanceof PatternProviderTearSlot) || !slot.isHighlightable()) {
            return;
        }

        int x = slot.x + 1;
        int y = slot.y;
        int w = 16;
        int h = 16;

        guiGraphics.hLine(x, x + w, y - 1, 0xFFdaffff);
        guiGraphics.hLine(x - 1, x + w, y + h, 0xFFdaffff);
        guiGraphics.vLine(x - 1, y - 2, y + h, 0xFFdaffff);
        guiGraphics.vLine(x + w, y - 2, y + h, 0xFFdaffff);
        guiGraphics.fillGradient(net.minecraft.client.renderer.RenderType.guiOverlay(), x, y, x + w, y + h, 0x669cd3ff, 0x669cd3ff, 0);
        ci.cancel();
    }

}
