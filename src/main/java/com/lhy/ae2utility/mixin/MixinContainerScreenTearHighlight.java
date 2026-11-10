package com.lhy.ae2utility.mixin;

import appeng.client.gui.AEBaseScreen;
import com.lhy.ae2utility.menu.PatternProviderTearSlot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AE2's old renderSlotHighlight is unused in 26.1; hook the two live vanilla layers. */
@Mixin(AbstractContainerScreen.class)
public abstract class MixinContainerScreenTearHighlight {
    @Shadow protected Slot hoveredSlot;

    @Inject(method = "extractSlotHighlightBack", at = @At("HEAD"), cancellable = true)
    private void ae2utility$tearHighlightBack(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if ((Object) this instanceof AEBaseScreen<?> && hoveredSlot instanceof PatternProviderTearSlot
                && hoveredSlot.isHighlightable()) {
            int x = hoveredSlot.x + 1;
            int y = hoveredSlot.y;
            graphics.fillGradient(x, y, x + 16, y + 16, 0x669cd3ff, 0x669cd3ff);
            ci.cancel();
        }
    }

    @Inject(method = "extractSlotHighlightFront", at = @At("HEAD"), cancellable = true)
    private void ae2utility$tearHighlightFront(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if ((Object) this instanceof AEBaseScreen<?> && hoveredSlot instanceof PatternProviderTearSlot
                && hoveredSlot.isHighlightable()) {
            int x = hoveredSlot.x + 1;
            int y = hoveredSlot.y;
            graphics.horizontalLine(x, x + 16, y - 1, 0xFFdaffff);
            graphics.horizontalLine(x - 1, x + 16, y + 16, 0xFFdaffff);
            graphics.verticalLine(x - 1, y - 2, y + 16, 0xFFdaffff);
            graphics.verticalLine(x + 16, y - 2, y + 16, 0xFFdaffff);
            ci.cancel();
        }
    }
}
