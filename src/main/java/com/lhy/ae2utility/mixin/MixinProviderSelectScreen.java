package com.lhy.ae2utility.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.lhy.ae2utility.client.InventoryPatternUploadQueue;
import com.lhy.ae2utility.client.RecipeTreeUploadProgressState;
import com.lhy.ae2utility.debug.InventoryPatternUploadDebug;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Mixin(targets = "com.extendedae_plus.client.screen.ProviderSelectScreen", remap = false)
public class MixinProviderSelectScreen {
    private static final int PAGE_SIZE = 6;

    @Shadow
    private EditBox searchBox;

    @Shadow
    private List<Long> fIds;

    @Shadow
    private List<String> fNames;

    @Shadow
    private List<Button> entryButtons;

    @Shadow
    private int page;

    @Inject(method = "init", at = @At("TAIL"))
    private void ae2utility$addCurrentPatternLabel(CallbackInfo ci) {
        if (searchBox == null) {
            return;
        }
        var font = net.minecraft.client.Minecraft.getInstance().font;
        if (font == null) {
            return;
        }
        String patternName = RecipeTreeUploadProgressState.currentPatternName();
        if (patternName == null || patternName.isBlank()) {
            return;
        }
        String machineName = RecipeTreeUploadProgressState.currentMachineName();
        String text = "正在处理样板: " + patternName + " (机器: " + (machineName == null || machineName.isBlank() ? "-" : machineName) + ")";
        String trimmed = font.plainSubstrByWidth(text, Math.max(40, searchBox.getWidth() - 4));
        var widget = new StringWidget(searchBox.getX() + 2, searchBox.getY() - 12, searchBox.getWidth() - 4, 9,
                Component.literal(trimmed), font);
        widget.setColor(0xFFFFFF);
        ((ScreenRenderableAccessor) (Object) this).ae2utility$addRenderableOnly(widget);
    }

    @Inject(method = "onChoose(IZ)V", at = @At("HEAD"), cancellable = true)
    private void ae2utility$batchUploadInventoryPatterns(int idx, boolean showStatusMessage, CallbackInfo ci) {
        InventoryPatternUploadDebug.info("provider_on_choose",
                "idx={} showStatusMessage={} selectingProvider={} visibleProviders={}",
                idx, showStatusMessage, InventoryPatternUploadQueue.isSelectingProvider(), fIds.size());
        if (!InventoryPatternUploadQueue.isSelectingProvider()) {
            return;
        }
        if (idx < 0 || idx >= fIds.size()) {
            InventoryPatternUploadDebug.warn("provider_on_choose", "invalid idx={} fIdsSize={}", idx, fIds.size());
            InventoryPatternUploadQueue.cancelSelection();
            ci.cancel();
            return;
        }
        InventoryPatternUploadDebug.info("provider_on_choose", "chosen providerId={} providerName={}", fIds.get(idx), fNames.get(idx));
        InventoryPatternUploadQueue.beginUploading(fIds.get(idx), fNames.get(idx));
        ((Screen) (Object) this).onClose();
        ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ae2utility$batchUploadByMouse(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || !InventoryPatternUploadQueue.isSelectingProvider()) {
            return;
        }
        InventoryPatternUploadDebug.info("provider_mouse_clicked",
                "mouseX={} mouseY={} page={} entryButtons={}", mouseX, mouseY, page, entryButtons.size());
        for (int i = 0; i < entryButtons.size(); i++) {
            Button btn = entryButtons.get(i);
            InventoryPatternUploadDebug.info("provider_mouse_clicked",
                    "buttonIndex={} x={} y={} width={} height={} visible={} active={} hover={}",
                    i, btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight(), btn.visible, btn.active,
                    btn.isMouseOver(mouseX, mouseY));
            if (!btn.visible || !btn.active) {
                continue;
            }
            if (!btn.isMouseOver(mouseX, mouseY)) {
                continue;
            }
            int actualIdx = page * PAGE_SIZE + i;
            InventoryPatternUploadDebug.info("provider_mouse_clicked",
                    "buttonHit buttonIndex={} actualIdx={} buttonLabel={}",
                    i, actualIdx, btn.getMessage().getString());
            if (actualIdx < 0 || actualIdx >= fIds.size()) {
                InventoryPatternUploadDebug.warn("provider_mouse_clicked",
                        "actualIdx out of range actualIdx={} fIdsSize={}", actualIdx, fIds.size());
                InventoryPatternUploadQueue.cancelSelection();
                cir.setReturnValue(true);
                return;
            }
            InventoryPatternUploadQueue.beginUploading(fIds.get(actualIdx), fNames.get(actualIdx));
            ((Screen) (Object) this).onClose();
            cir.setReturnValue(true);
            return;
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void ae2utility$clearInventoryBatchSelection(CallbackInfo ci) {
        InventoryPatternUploadDebug.info("provider_on_close", "selectingProvider={}", InventoryPatternUploadQueue.isSelectingProvider());
        InventoryPatternUploadQueue.cancelSelection();
    }
}
