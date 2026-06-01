package com.lhy.ae2utility.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.spongepowered.asm.mixin.Unique;

import com.lhy.ae2utility.client.Ae2UtilityClientConfig;
import com.lhy.ae2utility.client.EaepPendingProviderSearch;
import com.lhy.ae2utility.client.EaepRememberedProviderChoice;
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

    /** EAEP 的「唯一匹配自动上传」开关（持久化于 pinned_providers.json）。关闭时不得自动点选供应器。 */
    @Shadow
    private static boolean autoUploadUniqueMatchEnabled;

    @Unique
    private boolean ae2utility$sequentialUniqueProviderAutoFired;
    @Unique
    private boolean ae2utility$closeTriggeredByProviderChoice;

    @Unique
    private String ae2utility$screenSummary() {
        Minecraft mc = Minecraft.getInstance();
        Screen current = mc != null ? mc.screen : null;
        return "self@" + System.identityHashCode(this)
                + " currentScreen="
                + (current == null ? "null"
                        : current.getClass().getName() + "@" + System.identityHashCode(current));
    }

    @Unique
    private String ae2utility$stateSummary() {
        return ae2utility$screenSummary()
                + " awaitingAny=" + RecipeTreeUploadQueue.awaitingAnyProviderUpload()
                + " awaitingSequential=" + RecipeTreeUploadQueue.awaitingSequentialProviderUpload()
                + " selectingProvider=" + InventoryPatternUploadQueue.isSelectingProvider()
                + " closeByChoice=" + ae2utility$closeTriggeredByProviderChoice
                + " page=" + page
                + " providers=" + (fIds != null ? fIds.size() : -1)
                + " searchLen=" + (searchBox != null ? searchBox.getValue().length() : -1);
    }

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
                EaepPendingProviderSearch.markAutoAppliedFilter(pendingFilter);
            }
        }
        EaepUploadDebugLog.info("ProviderSelectScreen.init search prevLen={} appliedLen={} applied={} {}",
                prev.length(),
                pendingFilter != null ? pendingFilter.length() : -1,
                pendingFilter != null && !pendingFilter.isBlank() && prev.isBlank(),
                ae2utility$stateSummary());
        // 搜索词在 init 阶段已就绪时，直接在首帧渲染前完成自动点选（与 EAEP 原版唯一匹配静默上传一致，不弹窗）。
        if (ae2utility$tryAutoChooseProvider()) {
            return;
        }
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

    @Inject(method = "tick", at = @At("HEAD"))
    private void ae2utility$observeManualSearchEdit(CallbackInfo ci) {
        if (searchBox != null) {
            EaepPendingProviderSearch.observeCurrentSearchBoxValue(searchBox.getValue());
        }
    }

    /**
     * JEI/配方树顺序批量：EAEP 原版的 {@code tryAutoUploadIfUniqueMatch} 只在 init 里跑一次，
     * 而 ae2utility 的搜索关键字常常是「界面打开后」才同步过来的，原版那次判定会错过，
     * 因此这里在搜索框填好后补一次自动点选。判定条件与 EAEP 对齐：
     * <ul>
     *   <li>必须开启「唯一匹配自动上传」开关（{@link #autoUploadUniqueMatchEnabled}）；</li>
     *   <li>搜索框必须有真实过滤词（避免「网络里只有一个供应器/无过滤」时被误判为唯一并自动上传）；</li>
     *   <li>过滤后恰好只有一个供应器。</li>
     * </ul>
     */
    /**
     * 拦截 EAEP 原版 {@code init} 阶段的「唯一匹配自动上传」：当本批次已记住同名选择、但记住的供应器
     * 已不在当前列表（例如装满后被 EAEP 剔除，列表只剩一个异名供应器）时，取消原生自动上传，
     * 避免把样板误传到名字不同的供应器（如「聚合核心」→「聚合核心max」），改为停下弹窗让玩家手动选。
     */
    @Inject(method = "tryAutoUploadIfUniqueMatch", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2utility$suppressNativeAutoUploadWhenRememberedAbsent(CallbackInfo ci) {
        if (!Ae2UtilityClientConfig.reuseProviderWithinBatch()) {
            return;
        }
        if (!RecipeTreeUploadQueue.awaitingSequentialProviderUpload()
                && !InventoryPatternUploadQueue.isSelectingProvider()) {
            return;
        }
        String currentQuery = searchBox != null ? searchBox.getValue() : null;
        if (currentQuery == null || currentQuery.isBlank()) {
            return;
        }
        String remembered = EaepRememberedProviderChoice.lookup(currentQuery);
        if (remembered == null || fNames == null) {
            return;
        }
        boolean present = false;
        for (String n : fNames) {
            if (remembered.equals(n)) {
                present = true;
                break;
            }
        }
        if (!present) {
            JeiEncodeQueueDebugLog.info(
                    "ProviderSelectScreen suppress native unique auto-upload: remembered={} absent in list query={} providers={}",
                    remembered, currentQuery, fNames.size());
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void ae2utility$autoChooseWhenSingleProviderForSequentialQueue(CallbackInfo ci) {
        // 兜底：搜索词在 init 后才同步到达时，仍在 tick 里补一次自动点选（init 已能处理的常见情形不会走到这里）。
        ae2utility$tryAutoChooseProvider();
    }

    /**
     * 批量上传时尝试自动点选供应器（唯一匹配 / 同名复用）。返回 {@code true} 表示已触发点选并关闭界面。
     * 同时供 {@code init}（首帧前静默上传）与 {@code tick}（晚到搜索词兜底）调用，避免界面可见弹出。
     */
    @Unique
    private boolean ae2utility$tryAutoChooseProvider() {
        if (ae2utility$sequentialUniqueProviderAutoFired) {
            return false;
        }
        if (!RecipeTreeUploadQueue.awaitingSequentialProviderUpload()
                && !InventoryPatternUploadQueue.isSelectingProvider()) {
            return false;
        }
        if (fIds == null || fIds.isEmpty()) {
            return false;
        }
        if (!autoUploadUniqueMatchEnabled) {
            return false;
        }
        String currentQuery = searchBox != null ? searchBox.getValue() : null;
        boolean hasSearchFilter = currentQuery != null && !currentQuery.isBlank();
        if (!hasSearchFilter) {
            return false;
        }

        int chosenIdx = -1;
        boolean reused = false;
        // 仅当开启「批次内复用」时才查询本批次已记住的同名选择。
        String remembered = Ae2UtilityClientConfig.reuseProviderWithinBatch()
                ? EaepRememberedProviderChoice.lookup(currentQuery)
                : null;
        if (remembered != null && fNames != null) {
            // 本批次已选过供应器：只复用「同名」供应器（按名称匹配当前列表，不会错位）。
            // 若记住的供应器已不在列表（例如装满后被 EAEP 剔除），则绝不自动点选其它异名供应器——
            // 停下来弹窗让玩家手动选，避免误传到名字不同的供应器（如「聚合核心」→「聚合核心max」）。
            for (int i = 0; i < fNames.size(); i++) {
                if (remembered.equals(fNames.get(i))) {
                    chosenIdx = i;
                    reused = true;
                    break;
                }
            }
        } else if (fIds.size() == 1) {
            // 本批次尚未记住选择，且过滤后唯一供应器：与 EAEP 唯一匹配自动上传一致。
            chosenIdx = 0;
        }
        if (chosenIdx < 0) {
            return false;
        }

        ae2utility$sequentialUniqueProviderAutoFired = true;
        ae2utility$closeTriggeredByProviderChoice = true;
        JeiEncodeQueueDebugLog.info(
                "ProviderSelectScreen auto onChoose idx={} id={} reusedRemembered={} sequential={} inventoryBatch={} query={}",
                chosenIdx, fIds.get(chosenIdx), reused, RecipeTreeUploadQueue.awaitingSequentialProviderUpload(),
                InventoryPatternUploadQueue.isSelectingProvider(), currentQuery);
        ((ProviderSelectScreenInvoker) (Object) this).ae2utility$onChoose(chosenIdx, false);
        return true;
    }

    /**
     * 记录玩家（或自动逻辑）在批量上传会话内选择的供应器名称，供后续同搜索关键字、多供应器时复用。
     * 仅记录名称，复用时按当前列表名称重新匹配，避免列表顺序变化导致错位。
     */
    @Inject(method = "onChoose(IZ)V", at = @At("HEAD"))
    private void ae2utility$recordProviderChoiceForReuse(int idx, boolean showStatusMessage, CallbackInfo ci) {
        if (!Ae2UtilityClientConfig.reuseProviderWithinBatch()) {
            return;
        }
        if (!RecipeTreeUploadQueue.awaitingAnyProviderUpload()
                && !InventoryPatternUploadQueue.isSelectingProvider()) {
            return;
        }
        if (searchBox == null || fNames == null || idx < 0 || idx >= fNames.size()) {
            return;
        }
        String key = searchBox.getValue();
        if (key == null || key.isBlank()) {
            return;
        }
        EaepRememberedProviderChoice.record(key, fNames.get(idx));
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
        ae2utility$closeTriggeredByProviderChoice = true;
        ((Screen) (Object) this).onClose();
        ci.cancel();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ae2utility$batchUploadByMouse(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (RecipeTreeUploadQueue.awaitingAnyProviderUpload() || InventoryPatternUploadQueue.isSelectingProvider()) {
            EaepUploadDebugLog.info("ProviderSelectScreen.mouseClicked x={} y={} button={} {}",
                    mouseX, mouseY, button, ae2utility$stateSummary());
        }
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
            ae2utility$closeTriggeredByProviderChoice = true;
            ((Screen) (Object) this).onClose();
            cir.setReturnValue(true);
            return;
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"))
    private void ae2utility$logKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (RecipeTreeUploadQueue.awaitingAnyProviderUpload() || InventoryPatternUploadQueue.isSelectingProvider()) {
            EaepUploadDebugLog.info("ProviderSelectScreen.keyPressed key={} scan={} modifiers={} escape={} {}",
                    keyCode, scanCode, modifiers, keyCode == 256, ae2utility$stateSummary());
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void ae2utility$clearInventoryBatchSelection(CallbackInfo ci) {
        InventoryPatternUploadDebug.info("provider_on_close", "selectingProvider={}", InventoryPatternUploadQueue.isSelectingProvider());
        EaepUploadDebugLog.info("ProviderSelectScreen.onClose HEAD {}", ae2utility$stateSummary());
        InventoryPatternUploadQueue.cancelSelection();
        if (!ae2utility$closeTriggeredByProviderChoice && RecipeTreeUploadQueue.awaitingAnyProviderUpload()
                && !InventoryPatternUploadQueue.isSelectingProvider()) {
            JeiEncodeQueueDebugLog.info("ProviderSelectScreen sequential dismiss packet (onClose immediate)");
            EaepUploadDebugLog.info("ProviderSelectScreen sending dismiss packet from onClose {}", ae2utility$stateSummary());
            PacketDistributor.sendToServer(EaepSequentialProviderDismissPacket.INSTANCE);
        }
    }

    /** 关闭后重置“本次关闭是否由选中供应器触发”的标志，避免影响下一次打开。 */
    @Inject(method = "onClose", at = @At("TAIL"))
    private void ae2utility$resetCloseReason(CallbackInfo ci) {
        EaepUploadDebugLog.info("ProviderSelectScreen.onClose TAIL {}", ae2utility$stateSummary());
        ae2utility$closeTriggeredByProviderChoice = false;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void ae2utility$logForeignTopScreenWhileAlive(CallbackInfo ci) {
        if (!RecipeTreeUploadQueue.awaitingAnyProviderUpload() && !InventoryPatternUploadQueue.isSelectingProvider()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Screen current = mc != null ? mc.screen : null;
        if (current != null && current != (Object) this) {
            EaepUploadDebugLog.info("ProviderSelectScreen.tick sees foreign top screen {}", ae2utility$stateSummary());
        }
    }
}
