package com.lhy.ae2utility.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.MEStorage;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.me.common.MEStorageMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.Ae2UtilityServerConfig;
import com.lhy.ae2utility.api.pattern.PatternEncodingBatch;
import com.lhy.ae2utility.api.pattern.PatternEncodingRequest;
import com.lhy.ae2utility.api.pattern.PatternUploadMode;
import com.lhy.ae2utility.compat.WcwtCompat;
import com.lhy.ae2utility.debug.EaepUploadDebugLog;
import com.lhy.ae2utility.debug.JeiEncodeQueueDebugLog;
import com.lhy.ae2utility.integration.eaep.EaepDirectCompat;
import com.lhy.ae2utility.integration.eaep.EaepReflection;
import com.lhy.ae2utility.integration.eco.EcoReflection;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.EncodePreviewResultPacket;
import com.lhy.ae2utility.network.InvalidateCraftableCachePacket;
import com.lhy.ae2utility.network.ModNetworking;
import com.lhy.ae2utility.network.QueryEncodePreviewPacket;
import com.lhy.ae2utility.util.PatternEncodingPreview;

/**
 * 编码请求的服务端入口：解析终端上下文、执行服务端规则、扣除空白样板，并把编码好的样板交给玩家或上传路由
 * （ECO 合成子系统 → EAEP 装配矩阵 → EAEP 供应器选择界面）。
 *
 * <p>编码计算见 {@link PatternEncodingComputer}，空白样板账务见 {@link BlankPatternLedger}，
 * EAEP 搜索关键字见 {@link EaepProviderSearchKeys}。</p>
 */
public final class EncodePatternService {
    private enum EncodeOutcome {
        SUCCESS,
        FAILURE,
        BATCH_SKIP_DUPLICATE,
        BATCH_ABORT_NO_BLANK,
        /** EAEP 供应器界面已打开；同刻继续处理会破坏待上传队列 */
        EAEP_PROVIDER_UI_OPENED
    }

    private record EncodeContext(MEStorage inventory, IActionSource actionSource, @Nullable IGrid grid) {}

    private EncodePatternService() {}

    public static void handleApi(ServerPlayer player, PatternEncodingRequest request) {
        handle(player, toPayload(request, false, false, 0));
    }

    public static void handleApiBatch(ServerPlayer player, PatternEncodingBatch batch) {
        List<EncodePatternPacket> payloads = batch.requests().stream()
                .map(request -> toPayload(request, true, batch.fullRecipeCategory(), batch.sessionId()))
                .toList();
        handleBatch(player, payloads);
    }

    private static EncodePatternPacket toPayload(PatternEncodingRequest request, boolean sequential,
            boolean fullCategory, int sessionId) {
        return new EncodePatternPacket(
                request.inputs(), request.outputs(), request.recipeId(), request.patternName(),
                request.providerSearchKey(), request.providerDisplayName(),
                request.uploadMode() == PatternUploadMode.UPLOAD,
                request.substitute(), request.substituteFluids(), request.preserveInputOrder(),
                sequential, fullCategory, sessionId, request.craftingRecipeHint());
    }

    public static void handle(Player player, EncodePatternPacket payload) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        boolean sequential = payload.jeiSequentialQueue();
        EncodeOutcome outcome = encodePatternInternal(serverPlayer, payload, sequential);
        if (sequential) {
            if (outcome == EncodeOutcome.BATCH_SKIP_DUPLICATE) {
                SequentialUploadResultBridge.sendImmediateResult(serverPlayer, sequentialResultLabel(payload), true);
            } else if (outcome == EncodeOutcome.BATCH_ABORT_NO_BLANK) {
                SequentialUploadResultBridge.sendImmediateResult(serverPlayer, sequentialResultLabel(payload), false, true,
                        false, true);
            }
        }
    }

    /**
     * 悬停预览：走与真正编码相同的选材/编码，但不消耗空白样板。把即将写入样板的输入按 JEI 槽序回传。
     */
    public static void handlePreview(Player player, QueryEncodePreviewPacket payload) {
        if (!(player instanceof ServerPlayer serverPlayer) || payload == null || payload.recipe() == null) {
            return;
        }
        EncodePreviewResultPacket result = EncodePreviewResultPacket.empty(payload.requestId());
        try {
            result = previewEncodedPattern(serverPlayer, payload.requestId(), payload.recipe());
        } catch (Throwable ignored) {
        }
        ModNetworking.sendToPlayer(serverPlayer, result);
    }

    private static EncodePreviewResultPacket previewEncodedPattern(ServerPlayer serverPlayer, int requestId,
            EncodePatternPacket payload) {
        EncodeContext ctx = resolveEncodeContext(serverPlayer);
        if (ctx == null || (Ae2UtilityServerConfig.requireOpenPatternEncodingMenuForJei()
                && !serverPlayerHasOpenPatternEncodingLikeMenu(serverPlayer))) {
            return EncodePreviewResultPacket.empty(requestId);
        }
        List<GenericStack> out = payload.outputs();
        if (!PatternEncodingComputer.isValidPayload(payload.inputs(), out)) {
            return EncodePreviewResultPacket.empty(requestId);
        }
        Predicate<AEKey> craftable = PatternEncodingComputer.craftablePredicate(ctx.grid());
        List<GenericStack> in = PatternEncodingComputer.chooseInputs(payload, ctx.inventory(), craftable);
        if (in == null || in.isEmpty()) {
            return EncodePreviewResultPacket.empty(requestId);
        }
        PatternEncodingComputer.Computation computation = PatternEncodingComputer.compute(serverPlayer, payload, in, out,
                ctx.inventory(), craftable);
        if (computation.encodedPattern().isEmpty()) {
            return EncodePreviewResultPacket.empty(requestId);
        }
        PatternEncodingPreview panel = PatternEncodingPreview.fromEncodedPattern(
                computation.encodedPattern(), serverPlayer.level(), payload.substitute(), payload.substituteFluids());
        if (panel == null) {
            panel = PatternEncodingPreview.fromSlots(
                    PatternEncodingPreview.resolveMode(payload.recipeId(), payload.craftingCategoryHint(),
                            PatternEncodingPreview.presentCount(in), serverPlayer.level()),
                    computation.encodedInputs(), payload.outputs(), payload.substitute(), payload.substituteFluids());
        }
        return new EncodePreviewResultPacket(requestId, computation.encodedInputs(), panel.mode(),
                panel.inputs(), panel.outputs(), panel.substituteItems(), panel.substituteFluids(),
                panel.extraInputs(), panel.extraOutputs());
    }

    public static void handleBatch(ServerPlayer serverPlayer, List<EncodePatternPacket> patterns) {
        if (Ae2UtilityServerConfig.requireOpenPatternEncodingMenuForJei()
                && !serverPlayerHasOpenPatternEncodingLikeMenu(serverPlayer)) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.ae2utility.encode_rejected_require_open_encoding_menu"));
            return;
        }
        if (Ae2UtilityServerConfig.blockJeiFullCategoryBatchEncode()) {
            EncodePatternPacket anyFull = EncodeBatchPolicy.firstFullCategoryBatch(patterns);
            if (anyFull != null) {
                EncodeBulkSessionLimiter.notifyBlockedFullJeCategoryBatch(serverPlayer,
                        anyFull.bulkEncodeSessionId());
                return;
            }
        }
        List<EncodePatternPacket> toProcess = patterns;
        int mx = Ae2UtilityServerConfig.jeiBulkEncodeMaxPatternsPerSession();
        if (mx > 0 && patterns.size() > mx) {
            toProcess = EncodeBatchPolicy.cap(patterns, mx);
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.ae2utility.bulk_encode_truncated_to_server_batch", patterns.size(), mx)
                            .withStyle(ChatFormatting.GOLD));
        }
        int total = toProcess.size();
        for (int i = 0; i < toProcess.size(); i++) {
            EncodeOutcome outcome = encodePatternInternal(serverPlayer, toProcess.get(i), true);
            if (outcome == EncodeOutcome.BATCH_ABORT_NO_BLANK) {
                serverPlayer.sendSystemMessage(Component.translatable("message.ae2utility.batch_encode_blank_stopped", total - i));
                return;
            }
            if (outcome == EncodeOutcome.EAEP_PROVIDER_UI_OPENED) {
                int remaining = total - i - 1;
                if (remaining > 0) {
                    serverPlayer.sendSystemMessage(
                            Component.translatable("message.ae2utility.batch_encode_eaep_deferred", remaining).withStyle(ChatFormatting.GOLD));
                }
                return;
            }
        }
    }

    private static @Nullable EncodeContext resolveEncodeContext(ServerPlayer serverPlayer) {
        MEStorage inventory = null;
        IActionSource actionSource = null;
        IGrid grid = null;

        if (serverPlayer.containerMenu instanceof MEStorageMenu storageMenu) {
            ITerminalHost host = WcwtCompat.extractTerminalHost(storageMenu);
            if (host != null) {
                inventory = host.getInventory();
                if (host instanceof IActionHost ah) {
                    actionSource = IActionSource.ofPlayer(serverPlayer, ah);
                    if (ah.getActionableNode() != null) {
                        grid = ah.getActionableNode().getGrid();
                    }
                }
            }
        }

        if (inventory == null) {
            var resolution = WirelessTerminalContextResolver.resolve(serverPlayer);
            if (resolution.status() == WirelessTerminalContextResolver.Status.READY && resolution.host() != null) {
                WirelessTerminalMenuHost host = resolution.host();
                inventory = host.getInventory();
                actionSource = IActionSource.ofPlayer(serverPlayer, host);
                if (host.getActionableNode() != null) {
                    grid = host.getActionableNode().getGrid();
                }
            }
        }

        if (inventory == null) {
            return null;
        }
        if (actionSource == null) {
            actionSource = IActionSource.ofPlayer(serverPlayer);
        }
        return new EncodeContext(inventory, actionSource, grid);
    }

    private static boolean serverPlayerHasOpenPatternEncodingLikeMenu(ServerPlayer player) {
        return player.containerMenu instanceof MEStorageMenu
                && WcwtCompat.isPatternEncodingLikeMenu(player.containerMenu);
    }

    private static boolean eaepMatrixDuplicateAbortSingle(ServerPlayer serverPlayer, EncodePatternPacket payload,
            ItemStack encodedPattern, boolean canUploadToMatrix, @Nullable IGrid terminalGrid) {
        if (!payload.shiftDown() || !ModList.get().isLoaded("extendedae_plus")) {
            return false;
        }
        // 处理样板不进装配矩阵，矩阵重复检测对其无意义（且可能误判），与 EAEP 一致仅对可进矩阵的样板检测。
        if (!canUploadToMatrix) {
            return false;
        }
        IGrid eaepGrid = EaepReflection.findPlayerGrid(serverPlayer);
        if (eaepGrid == null) {
            eaepGrid = terminalGrid;
        }
        if (eaepGrid == null) {
            return false;
        }
        if (EaepReflection.matrixContainsPattern(eaepGrid, encodedPattern)) {
            serverPlayer.sendSystemMessage(Component.translatable("extendedae_plus.message.matrix.duplicate"));
            SequentialUploadResultBridge.sendImmediateResult(serverPlayer,
                    payload.patternName().isBlank() ? sequentialResultLabel(payload) : payload.patternName(), false);
            return true;
        }
        return false;
    }

    /**
     * 与 EAEP 装配矩阵查重对齐：单次上传时，若 ECO 合成子系统已存在相同样板，则拦截并提示，不再重复上传。
     * 仅对「可进矩阵」的合成/锻造/切石样板有意义；处理样板不进 ECO。批量模式由网络去重统一处理，不在此提示。
     */
    private static boolean ecoDuplicateAbortSingle(ServerPlayer serverPlayer, EncodePatternPacket payload,
            ItemStack encodedPattern, boolean canUploadToMatrix, @Nullable IGrid terminalGrid) {
        if (!payload.shiftDown() || !canUploadToMatrix
                || !EcoReflection.isLoaded()) {
            return false;
        }
        if (!EcoReflection.containsPattern(terminalGrid, encodedPattern)) {
            return false;
        }
        serverPlayer.sendSystemMessage(
                Component.translatable("message.ae2utility.eco_pattern_duplicate").withStyle(ChatFormatting.GOLD));
        SequentialUploadResultBridge.sendImmediateResult(serverPlayer,
                payload.patternName().isBlank() ? sequentialResultLabel(payload) : payload.patternName(), false);
        return true;
    }

    private static void giveEncodedPatternNoUpload(ServerPlayer player, ItemStack encodedPattern) {
        ItemStack stack = encodedPattern.copy();
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    private static String sequentialResultLabel(EncodePatternPacket payload) {
        if (payload.patternName() != null && !payload.patternName().isBlank()) {
            return payload.patternName();
        }
        if (payload.recipeId() != null) {
            return payload.recipeId().toString();
        }
        return "-";
    }

    private static EncodeOutcome encodePatternInternal(ServerPlayer serverPlayer, EncodePatternPacket payload, boolean batchMode) {
        EncodeContext ctx = resolveEncodeContext(serverPlayer);
        if (ctx == null) {
            if (payload.jeiSequentialQueue()) {
                SequentialUploadResultBridge.sendImmediateResult(serverPlayer, sequentialResultLabel(payload), false);
            }
            return EncodeOutcome.FAILURE;
        }

        if (Ae2UtilityServerConfig.requireOpenPatternEncodingMenuForJei()
                && !serverPlayerHasOpenPatternEncodingLikeMenu(serverPlayer)) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.ae2utility.encode_rejected_require_open_encoding_menu"));
            if (payload.jeiSequentialQueue()) {
                SequentialUploadResultBridge.sendImmediateResult(serverPlayer, sequentialResultLabel(payload), false);
            } else {
                SequentialUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            }
            return EncodeOutcome.FAILURE;
        }

        if (EncodeBulkSessionLimiter.rejectIfLimited(serverPlayer, payload)) {
            return EncodeOutcome.FAILURE;
        }

        IActionSource actionSource = ctx.actionSource();
        MEStorage inventory = ctx.inventory();
        IGrid grid = ctx.grid();

        SequentialUploadResultBridge.clearPendingName(serverPlayer);
        SequentialUploadContextBridge.clear(serverPlayer);

        List<GenericStack> out = payload.outputs();
        if (!PatternEncodingComputer.isValidPayload(payload.inputs(), out)) {
            serverPlayer.sendSystemMessage(Component.translatable("message.ae2utility.jeict_pattern_draft_invalid"));
            SequentialUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            return EncodeOutcome.FAILURE;
        }

        Predicate<AEKey> craftable = PatternEncodingComputer.craftablePredicate(grid);
        List<GenericStack> in = PatternEncodingComputer.chooseInputs(payload, inventory, craftable);
        if (in == null) {
            serverPlayer.sendSystemMessage(Component.translatable("message.ae2utility.jeict_pattern_draft_invalid"));
            SequentialUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            return EncodeOutcome.FAILURE;
        }
        if (in.isEmpty()) {
            SequentialUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            return EncodeOutcome.FAILURE;
        }

        PatternEncodingComputer.Computation computation = PatternEncodingComputer.compute(serverPlayer, payload, in, out,
                inventory, craftable);
        ItemStack encodedPattern = computation.encodedPattern();
        boolean canUploadToMatrix = computation.canUploadToMatrix();

        if (encodedPattern.isEmpty()) {
            SequentialUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            return EncodeOutcome.FAILURE;
        }

        EaepReflection.writeEncoderAttribution(serverPlayer, encodedPattern);

        if (batchMode && EncodePatternDuplicateChecker.batchNetworkAlreadyContains(serverPlayer, grid, encodedPattern)) {
            JeiEncodeQueueDebugLog.info(
                    "encode batch skip duplicate player={} recipeId={} gridNull={} jeiSequentialQueue={}",
                    serverPlayer.getScoreboardName(), payload.recipeId(), grid == null, payload.jeiSequentialQueue());
            return EncodeOutcome.BATCH_SKIP_DUPLICATE;
        }

        if (!batchMode && eaepMatrixDuplicateAbortSingle(serverPlayer, payload, encodedPattern, canUploadToMatrix, grid)) {
            return EncodeOutcome.FAILURE;
        }

        if (!batchMode && ecoDuplicateAbortSingle(serverPlayer, payload, encodedPattern, canUploadToMatrix, grid)) {
            return EncodeOutcome.FAILURE;
        }

        BlankPatternLedger.Source blankSource = BlankPatternLedger.consumeOne(serverPlayer, inventory, actionSource);
        if (blankSource == null) {
            if (!batchMode) {
                serverPlayer.sendSystemMessage(Component.translatable("message.ae2utility.encode_failed_no_blank_named",
                        payload.patternName().isBlank() ? "-" : payload.patternName()));
                SequentialUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            }
            return batchMode ? EncodeOutcome.BATCH_ABORT_NO_BLANK : EncodeOutcome.FAILURE;
        }

        try {
            EaepProviderSearchKeys.writeToPattern(serverPlayer, payload, encodedPattern);

            // ECO 优先：合成/锻造/切石样板（canUploadToMatrix）若网络存在 ECO 合成子系统，先尝试塞入 ECO；
            // ECO 满或无该子系统时返回 false，继续走下方原有上传逻辑（EAEP 装配矩阵 / 供应器）。
            if (payload.shiftDown() && canUploadToMatrix
                    && EcoReflection.isLoaded()
                    && EcoReflection.tryInsertPattern(grid, encodedPattern)) {
                String okName = payload.patternName().isBlank() ? sequentialResultLabel(payload) : payload.patternName();
                SequentialUploadResultBridge.sendImmediateResult(serverPlayer, okName, true);
                sendCraftableCacheRefreshIfNonEmpty(serverPlayer, payload);
                return EncodeOutcome.SUCCESS;
            }

            if (payload.shiftDown() && ModList.get().isLoaded("extendedae_plus")) {
                try {
                    IGrid eaepGrid = EaepReflection.findPlayerGrid(serverPlayer);
                    if (eaepGrid == null) {
                        eaepGrid = grid;
                    }

                    EaepUploadDebugLog.info(
                            "EncodePattern shift EAEP start player={} patternName={} recipeId={} canMatrixHint={} eaepGridNull={}",
                            serverPlayer.getScoreboardName(), payload.patternName(), payload.recipeId(), canUploadToMatrix,
                            eaepGrid == null);

                        if (eaepGrid == null) {
                            if (payload.jeiSequentialQueue()) {
                                BlankPatternLedger.refund(serverPlayer, inventory, actionSource, blankSource);
                                serverPlayer.sendSystemMessage(
                                        Component.translatable("message.ae2utility.recipe_shift_batch_aborted_no_network")
                                                .withStyle(ChatFormatting.RED));
                                SequentialUploadResultBridge.sendImmediateResult(serverPlayer, sequentialResultLabel(payload), false,
                                        true);
                                return EncodeOutcome.FAILURE;
                            }
                    } else {
                        /*
                         * 与 ExtendedAE Plus 对齐：只有「合成/锻造/切石」类样板（canUploadToMatrix=true）才尝试装配矩阵；
                         * 处理样板（processing）不进装配矩阵，直接走供应器待定队列，避免误判为「矩阵已满」而上传失败。
                         */
                        if (canUploadToMatrix) {
                            boolean uploadedMatrix = EaepDirectCompat
                                    .uploadPatternToMatrix(serverPlayer, encodedPattern, eaepGrid);
                            EaepUploadDebugLog.info(
                                    "EncodePattern matrix upload attempted recipeId={} canMatrixHint={} uploaded={}",
                                    payload.recipeId(), canUploadToMatrix, uploadedMatrix);

                            if (uploadedMatrix) {
                                BlankPatternLedger.releaseHold(serverPlayer);
                                String okName = payload.patternName().isBlank() ? sequentialResultLabel(payload) : payload.patternName();
                                SequentialUploadResultBridge.sendImmediateResult(serverPlayer, okName, true);
                                sendCraftableCacheRefreshIfNonEmpty(serverPlayer, payload);
                                return EncodeOutcome.SUCCESS;
                            }

                            /*
                             * JEI Ctrl+Shift 顺序批量：preserveInputOrder=false（JEICT 批量为 true）。
                             * 矩阵与 EAEP upload 均未接受时视作「矩阵满/无法再塞」一类，中止整批，避免逐项弹供应器又把失败配方收藏满 JEI。
                             * 仅对「可进矩阵」的样板适用；处理样板不会到达此分支。
                             */
                            boolean jeiSequentialShiftJeBulkOnly = payload.jeiSequentialQueue()
                                    && payload.shiftDown()
                                    && !payload.preserveInputOrder();
                            if (jeiSequentialShiftJeBulkOnly) {
                                BlankPatternLedger.releaseHold(serverPlayer);
                                giveEncodedPatternNoUpload(serverPlayer, encodedPattern);
                                serverPlayer.sendSystemMessage(
                                        Component.translatable("message.ae2utility.shift_batch_aborted_matrix_reject").withStyle(
                                                ChatFormatting.GOLD));
                                SequentialUploadResultBridge.sendImmediateResult(serverPlayer, sequentialResultLabel(payload), false, true);
                                sendCraftableCacheRefreshIfNonEmpty(serverPlayer, payload);
                                return EncodeOutcome.FAILURE;
                            }
                        }

                        EaepProviderSearchKeys.syncToClient(serverPlayer, payload);
                        SequentialUploadContextBridge.rememberGrid(serverPlayer, eaepGrid);
                        SequentialUploadContextBridge.rememberPendingSearchKey(serverPlayer,
                                EaepProviderSearchKeys.rawKeyForSync(serverPlayer, payload));
                        SequentialUploadContextBridge.rememberPendingProviderDisplayName(serverPlayer, payload.providerDisplayName());
                        EaepReflection.clearPendingCtrlQUpload(serverPlayer);
                        SequentialUploadResultBridge.rememberPendingName(serverPlayer, sequentialResultLabel(payload));
                        List<AEKey> pendingCraftableRefresh = collectCraftableKeysForRefresh(payload);
                        if (!pendingCraftableRefresh.isEmpty()) {
                            SequentialUploadResultBridge.rememberPendingCraftableRefresh(serverPlayer, pendingCraftableRefresh);
                        }
                        com.lhy.ae2utility.integration.eaep.EaepReflection
                                .beginPendingCtrlQUpload(serverPlayer, encodedPattern.copyWithCount(1));

                        /*
                         * 与 ExtendedAE Plus 对齐：不再在服务端「凭记忆的 providerId 直接上传、跳过供应器界面」。
                         * 原实现存在两个问题：
                         *   1) 不读取 EAEP 的「唯一匹配自动上传」开关，开关关闭后仍会自动上传；
                         *   2) CtrlQ 路径的 providerId 是 -1-index 这种随供应器列表顺序变化的临时索引，
                         *      批量过程中列表（空位/可见性）一变就会上传到错误的供应器。
                         * 现在统一走「打开供应器界面」的路径，由 EAEP 客户端 ProviderSelectScreen 的开关 +
                         * 唯一匹配逻辑（tryAutoUploadIfUniqueMatch）决定是否自动上传，从而保证行为对齐。
                         */
                        if (!batchMode || payload.jeiSequentialQueue()) {
                            BlankPatternLedger.holdForPendingProvider(serverPlayer, inventory, actionSource, blankSource);
                            SequentialUploadResultBridge.sendAwaitingProviderUpload(serverPlayer, sequentialResultLabel(payload));
                        }
                        EaepUploadDebugLog.info(
                                "EncodePattern EAEP beginPendingCtrlQUpload sequential={} recipeId={} patternItem={} rememberedGrid=true",
                                payload.jeiSequentialQueue(), payload.recipeId(), encodedPattern.getItem());
                        return EncodeOutcome.EAEP_PROVIDER_UI_OPENED;
                    }
                } catch (Throwable e) {
                    SequentialUploadResultBridge.clearPendingName(serverPlayer);
                    boolean refundedShiftBlank = BlankPatternLedger.refundHoldIfPending(serverPlayer);
                    EaepUploadDebugLog.error("EncodePattern EAEP branch threw patternName=" + payload.patternName(), e);
                    String failName = payload.jeiSequentialQueue() ? sequentialResultLabel(payload) : payload.patternName();
                    SequentialUploadResultBridge.sendImmediateResult(serverPlayer, failName, false);
                    if (!refundedShiftBlank) {
                        BlankPatternLedger.refund(serverPlayer, inventory, actionSource, blankSource);
                    }
                    return EncodeOutcome.FAILURE;
                }
            }
            giveEncodedPatternNoUpload(serverPlayer, encodedPattern);
            BlankPatternLedger.releaseHold(serverPlayer);
            SequentialUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            sendCraftableCacheRefreshIfNonEmpty(serverPlayer, payload);
            return EncodeOutcome.SUCCESS;
        } catch (Throwable e) {
            SequentialUploadResultBridge.clearPendingName(serverPlayer);
            Ae2UtilityMod.LOGGER.error("Error encoding pattern: ", e);
            SequentialUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            if (!BlankPatternLedger.refundHoldIfPending(serverPlayer)) {
                BlankPatternLedger.refund(serverPlayer, inventory, actionSource, blankSource);
            }
            return EncodeOutcome.FAILURE;
        }
    }

    private static void sendCraftableCacheRefreshIfNonEmpty(ServerPlayer serverPlayer, EncodePatternPacket payload) {
        List<AEKey> keys = collectCraftableKeysForRefresh(payload);
        if (!keys.isEmpty()) {
            ModNetworking.sendToPlayer(serverPlayer, new InvalidateCraftableCachePacket(keys));
        }
    }

    private static List<AEKey> collectCraftableKeysForRefresh(EncodePatternPacket payload) {
        Set<AEKey> keys = new LinkedHashSet<>();
        for (List<GenericStack> alts : payload.inputs()) {
            if (alts == null) {
                continue;
            }
            for (GenericStack gs : alts) {
                if (gs != null && gs.what() != null) {
                    keys.add(gs.what());
                }
            }
        }
        for (GenericStack gs : payload.outputs()) {
            if (gs != null && gs.what() != null) {
                keys.add(gs.what());
            }
        }
        return List.copyOf(keys);
    }
}
