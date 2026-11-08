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

import com.lhy.ae2utility.client.gui.PatternProviderTearSlotChrome;
import com.lhy.ae2utility.menu.PatternProviderTearSlot;

@Mixin(AEBaseScreen.class)
public abstract class MixinAEBaseScreen {
    private static final int SLOT_SIZE = 18;
    private static final int TEAR_SLOT_VANILLA_TOPRIGHT_PAD_RIGHT = -2;
    private static final int TEAR_SLOT_VANILLA_TOPRIGHT_PAD_TOP = 6;
    private static final int TEAR_SLOT_VANILLA_NUDGE_X = 17;
    private static final int TEAR_SLOT_VANILLA_NUDGE_Y = -1;

    private static final List<Component> TEAR_SLOT_EMPTY_TOOLTIP = List.of(
            Component.translatable("gui.ae2utility.pattern_provider.nbt_tear_slot.title")
                    .withStyle(ChatFormatting.GRAY));

    @Inject(method = "init", at = @At("TAIL"))
    private void ae2utility$positionPatternProviderTearSlot(CallbackInfo ci) {
        ae2utility$repositionPatternProviderTearSlot();
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    shift = At.Shift.BEFORE))
    private void ae2utility$repositionTearAfterWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        if (ae2utility$findPatternProviderTearSlot() != null) {
            ae2utility$repositionPatternProviderTearSlot();
        }
    }

    private void ae2utility$repositionPatternProviderTearSlot() {
        AEBaseScreen<?> screen = (AEBaseScreen<?>) (Object) this;
        PatternProviderTearSlot tear = ae2utility$findPatternProviderTearSlot();
        if (tear == null) {
            return;
        }
        var inner = ((AEBaseScreenInvoker) (Object) this).ae2utility$getBounds(false);
        int x = inner.getWidth() - SLOT_SIZE - TEAR_SLOT_VANILLA_TOPRIGHT_PAD_RIGHT;
        SlotPosAccessor pos = (SlotPosAccessor) (Object) tear;
        pos.ae2utility$setX(Math.max(0, x) + TEAR_SLOT_VANILLA_NUDGE_X);
        pos.ae2utility$setY(Math.max(0, TEAR_SLOT_VANILLA_TOPRIGHT_PAD_TOP) + TEAR_SLOT_VANILLA_NUDGE_Y);
    }

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void ae2utility$drawPatternProviderTearChrome(GuiGraphics graphics, float partialTick, int mouseX, int mouseY,
            CallbackInfo ci) {
        AEBaseScreen<?> screen = (AEBaseScreen<?>) (Object) this;
        PatternProviderTearSlot tear = ae2utility$findPatternProviderTearSlot();
        if (tear != null) {
            SlotPosAccessor pos = (SlotPosAccessor) (Object) tear;
            PatternProviderTearSlotChrome.draw(graphics, screen.getGuiLeft() + pos.ae2utility$getX() + 2,
                    screen.getGuiTop() + pos.ae2utility$getY());
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void ae2utility$patternProviderTearOverlays(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        AEBaseScreen<?> screen = (AEBaseScreen<?>) (Object) this;
        PatternProviderTearSlot tear = ae2utility$findPatternProviderTearSlot();
        if (tear == null) {
            return;
        }
        if (tear.getItem().isEmpty() && ae2utility$isMouseOverSlot(screen, tear, mouseX, mouseY)) {
            screen.drawTooltip(graphics, mouseX, mouseY, TEAR_SLOT_EMPTY_TOOLTIP);
        }
    }

    private static boolean ae2utility$isMouseOverSlot(AEBaseScreen<?> screen, Slot slot, int mouseX, int mouseY) {
        SlotPosAccessor pos = (SlotPosAccessor) (Object) slot;
        int x = screen.getGuiLeft() + pos.ae2utility$getX() + 2;
        int y = screen.getGuiTop() + pos.ae2utility$getY();
        return mouseX >= x && mouseX < x + 18 && mouseY >= y && mouseY < y + 18;
    }

    private PatternProviderTearSlot ae2utility$findPatternProviderTearSlot() {
        AEBaseScreen<?> screen = (AEBaseScreen<?>) (Object) this;
        for (Slot slot : screen.getMenu().slots) {
            if (slot instanceof PatternProviderTearSlot pts) {
                return pts;
            }
        }
        return null;
    }
}
