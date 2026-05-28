package com.lhy.ae2utility.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.spongepowered.asm.mixin.Unique;

import com.lhy.ae2utility.client.EaepPendingProviderSearch;
import com.lhy.ae2utility.debug.EaepUploadDebugLog;
import com.lhy.ae2utility.client.InventoryPatternUploadQueue;
import com.lhy.ae2utility.client.RecipeTreeUploadProgressState;
import com.lhy.ae2utility.client.RecipeTreeUploadQueue;
import com.lhy.ae2utility.debug.InventoryPatternUploadDebug;
import com.lhy.ae2utility.debug.JeiEncodeQueueDebugLog;
import com.lhy.ae2utility.network.EaepSequentialProviderDismissPacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

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

    @Unique
    private boolean ae2utility$sequentialUniqueProviderAutoFired;

    @Inject(method = "init", at = @At("TAIL"))
    private void ae2utility$addCurrentPatternLabel(CallbackInfo ci) {
        if (searchBox == null) {
            return;
        }
        String prev = searchBox.getValue();
        String pendingFilter = EaepPendingProviderSearch.populateSearchBoxFilterPreference();
        if (pendingFilter != null && !pendingFilter.isBlank()) {
            if (prev.isBlank()) {
                searchBox.setValue(pendingFilter);
            }
        }
        EaepUploadDebugLog.info("ProviderSelectScreen.init search prevLen={} appliedLen={} applied={}", prev.length(),
                pendingFilter != null ? pendingFilter.length() : -1,
                pendingFilter != null && !pendingFilter.isBlank() && prev.isBlank());
        var font = net.minecraft.client.Minecraft.getInstance().font;
        if (font == null) {
            return;
        }
        String patternName = RecipeTreeUploadProgressState.currentPatternName();
        if (patternName == null || patternName.isBlank() || "-".equals(patternName)) {
            var rid = RecipeTreeUploadProgressState.currentRecipeId();
            if (rid != null) {
                patternName = rid.toString();
            }
        }
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

    /**
     * JEI/配方树顺序批量：EAEP「仅一个供应器」时原版逻辑有时不会自动点；这里代为 onChoose(0)，与终端里唯一匹配自动上传一致。
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void ae2utility$autoChooseWhenSingleProviderForSequentialQueue(CallbackInfo ci) {
        if (ae2utility$sequentialUniqueProviderAutoFired) {
            return;
        }
        if (!RecipeTreeUploadQueue.awaitingSequentialProviderUpload()
                && !InventoryPatternUploadQueue.isSelectingProvider()) {
            return;
        }
        if (fIds == null || fIds.size() != 1) {
            return;
        }
        ae2utility$sequentialUniqueProviderAutoFired = true;
        JeiEncodeQueueDebugLog.info("ProviderSelectScreen auto onChoose single provider id={} sequential={} inventoryBatch={}",
                fIds.get(0), RecipeTreeUploadQueue.awaitingSequentialProviderUpload(), InventoryPatternUploadQueue.isSelectingProvider());
        ((ProviderSelectScreenInvoker) (Object) this).ae2utility$onChoose(0, false);
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

    /** JEI 顺序 Shift：EAEP 未通过 returnPending mixin 发包时客户端会一直被冻结；推迟一帧向服务端兜底请求归还并报失败。 */
    @Inject(method = "onClose", at = @At("TAIL"))
    private void ae2utility$dismissSequentialShiftIfProbablyAbandoned(CallbackInfo ci) {
        if (!RecipeTreeUploadQueue.awaitingSequentialProviderUpload()) {
            return;
        }
        if (InventoryPatternUploadQueue.isSelectingProvider()) {
            return;
        }
        Minecraft.getInstance().execute(() -> {
            if (!RecipeTreeUploadQueue.awaitingSequentialProviderUpload()) {
                return;
            }
            if (InventoryPatternUploadQueue.isSelectingProvider()) {
                return;
            }
            JeiEncodeQueueDebugLog.info("ProviderSelectScreen sequential dismiss packet (post-close heuristic)");
            PacketDistributor.sendToServer(EaepSequentialProviderDismissPacket.INSTANCE);
        });
    }
}
