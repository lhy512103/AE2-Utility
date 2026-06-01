package com.lhy.ae2utility.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import com.lhy.ae2utility.debug.EaepUploadDebugLog;
import com.lhy.ae2utility.debug.JeiEncodeQueueDebugLog;
import com.lhy.ae2utility.integration.eaep.EaepProviderListRequest;
import com.lhy.ae2utility.jei.JeiBookmarkUtil;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.RecipeTreeUploadResultPacket;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RecipeTreeUploadQueue {
    /** 每条在服务端已成功落矩阵后，客户端发下一条前的间隔（Tick）。供应器界面路径由玩家操作耗时，此处只影响纯快速路径。 */
    private static final int NEXT_PACKET_DELAY_TICKS = 3;
    /** 超过此数量时汇总消息只显示个数，不再逐条列出样板名称（避免刷屏）。 */
    private static final int SUMMARY_DETAIL_NAME_THRESHOLD = 20;

    private static final Deque<EncodePatternPacket> PENDING = new ArrayDeque<>();
    private static final Deque<EncodePatternPacket> IN_FLIGHT = new ArrayDeque<>();
    private static final List<String> SUCCEEDED = new ArrayList<>();
    private static final List<String> FAILED = new ArrayList<>();
    private static int missingBlankFailCount;
    private static int expectedResults;
    private static int receivedResults;
    private static int waitTicks;
    private static boolean waitingForProviderScreen;
    /** 已发送编码请求，等待服务端任意 RecipeTreeUploadResultPacket（含 awaiting）。 */
    private static boolean frozenUntilServerEncodeResponse;
    /** 服务端正在等待玩家在 EAEP 供应器界面完成上传；解冻需 flushPendingResult 的最终结果包。 */
    private static boolean awaitingProviderUploadCompletion;

    private RecipeTreeUploadQueue() {
    }

    /**
     * JEI 等入口：若当前仍有未处理完的包（含等待供应器），拒绝第二次 start，避免误触清空正在上传的队列。
     */
    public static boolean start(List<EncodePatternPacket> packets) {
        return startInternal(packets, false);
    }

    /**
     * 配方树总览「上传」等入口：用户显式开新的一批时，先丢弃客户端卡住的旧会话（例如 EAEP 界面关掉后永远等不到 FINAL）。
     */
    public static boolean startReplacing(List<EncodePatternPacket> packets) {
        return startInternal(packets, true);
    }

    /**
     * 顺序批量（JEI / 配方树）正在等 EAEP 供应器界面；供客户端在「唯一供应器」时自动确认。
     */
    public static boolean awaitingSequentialProviderUpload() {
        return awaitingProviderUploadCompletion
                && !IN_FLIGHT.isEmpty()
                && IN_FLIGHT.peekFirst().jeiSequentialQueue();
    }

    /** 当前是否有任意 EncodePattern 会话正在等待 EAEP 供应器界面完成上传/取消。 */
    public static boolean awaitingAnyProviderUpload() {
        return awaitingProviderUploadCompletion && !IN_FLIGHT.isEmpty();
    }

    private static boolean isSessionActivelyBlocking() {
        return !PENDING.isEmpty()
                || !IN_FLIGHT.isEmpty()
                || awaitingProviderUploadCompletion
                || frozenUntilServerEncodeResponse
                || waitTicks > 0
                || waitingForProviderScreen;
    }

    private static void clearSessionFully() {
        PENDING.clear();
        IN_FLIGHT.clear();
        SUCCEEDED.clear();
        FAILED.clear();
        missingBlankFailCount = 0;
        expectedResults = 0;
        receivedResults = 0;
        waitTicks = 0;
        waitingForProviderScreen = false;
        frozenUntilServerEncodeResponse = false;
        awaitingProviderUploadCompletion = false;
        RecipeTreeUploadProgressState.clear();
        EaepPendingProviderSearch.forgetResolvedFilterReuse();
    }

    private static boolean startInternal(List<EncodePatternPacket> packets, boolean replaceIfBusy) {
        if (packets.isEmpty()) {
            JeiEncodeQueueDebugLog.warn("start rejected: empty packet list");
            return false;
        }
        if (expectedResults > 0) {
            if (replaceIfBusy) {
                JeiEncodeQueueDebugLog.info(
                        "start replacing previous client session recv={}/{} pending={} inFlight={} awaiting={}",
                        receivedResults, expectedResults, PENDING.size(), IN_FLIGHT.size(), awaitingProviderUploadCompletion);
                clearSessionFully();
            } else if (isSessionActivelyBlocking()) {
                JeiEncodeQueueDebugLog.warn(
                        "start rejected: session already active pending={} inFlight={} awaitingProvider={} frozen={} recv={}/{} inFlightHeadRecipeId={}",
                        PENDING.size(), IN_FLIGHT.size(), awaitingProviderUploadCompletion, frozenUntilServerEncodeResponse,
                        receivedResults, expectedResults, IN_FLIGHT.isEmpty() ? null : IN_FLIGHT.peekFirst().recipeId());
                return false;
            } else {
                JeiEncodeQueueDebugLog.warn(
                        "start heal orphan counters (no active work) recv={}/{} -> clear",
                        receivedResults, expectedResults);
                clearSessionFully();
            }
        }
        PENDING.clear();
        IN_FLIGHT.clear();
        SUCCEEDED.clear();
        FAILED.clear();
        missingBlankFailCount = 0;
        EaepPendingProviderSearch.forgetResolvedFilterReuse();
        PENDING.addAll(packets);
        expectedResults = packets.size();
        receivedResults = 0;
        waitTicks = 0;
        waitingForProviderScreen = false;
        frozenUntilServerEncodeResponse = false;
        awaitingProviderUploadCompletion = false;
        JeiEncodeQueueDebugLog.info(
                "start queueSize={} expectedResults={} headRecipeId={}",
                packets.size(), expectedResults, packets.getFirst().recipeId());
        sendNext();
        return true;
    }

    /**
     * 终止 JEI Shift 顺序队列与相关本地状态（不通知服务端；服务端已消耗的空白样板等不受影响）。
     */
    public static void cancelAll() {
        clearSessionFully();
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.ae2utility.uploads_cancelled").withStyle(ChatFormatting.GOLD),
                    false);
        }
    }

    /** 由服务端下发的同步中止使用，不产生聊天提示（服务端或以数据包另行通知）。 */
    public static void cancelAllQuiet() {
        clearSessionFully();
    }

    public static void tick() {
        if (frozenUntilServerEncodeResponse || awaitingProviderUploadCompletion) {
            return;
        }

        if (PENDING.isEmpty() && !waitingForProviderScreen && waitTicks <= 0) {
            return;
        }

        boolean providerScreenOpen = isEaepProviderSelectScreen(Minecraft.getInstance().screen);
        if (providerScreenOpen) {
            waitingForProviderScreen = true;
            return;
        }

        if (waitingForProviderScreen) {
            waitingForProviderScreen = false;
            waitTicks = Math.max(waitTicks, 4);
            JeiEncodeQueueDebugLog.info("tick closed ProviderSelectScreen waitTicks=>{}", waitTicks);
            return;
        }

        if (waitTicks > 0) {
            waitTicks--;
            if (waitTicks == 0) {
                JeiEncodeQueueDebugLog.info(
                        "tick delay elapsed pendingLeft={} inFlightSize={} frozen={} awaiting={}",
                        PENDING.size(), IN_FLIGHT.size(), frozenUntilServerEncodeResponse, awaitingProviderUploadCompletion);
                sendNext();
            }
        }
    }

    public static void handleResult(RecipeTreeUploadResultPacket packet) {
        EaepUploadDebugLog.info(
                "RecipeTreeUploadQueue.handleResult uploaded={} awaiting={} abort={} purge={} missingBlank={} inFlight={} pending={} frozen={} awaitingProvider={}",
                packet.uploaded(),
                packet.awaitingProviderCompletion(),
                packet.abortRemainingBatch(),
                packet.purgeRemainingQueuedSameEaepMachine(),
                packet.missingBlankPatternFailure(),
                IN_FLIGHT.size(),
                PENDING.size(),
                frozenUntilServerEncodeResponse,
                awaitingProviderUploadCompletion);
        if (packet.abortRemainingBatch()) {
            int missingBlankTargetCount = Math.max(1, IN_FLIGHT.size() + PENDING.size());
            int succeededBeforeAbort = SUCCEEDED.size();
            frozenUntilServerEncodeResponse = false;
            awaitingProviderUploadCompletion = false;
            clearSessionFully();
            var playerAbort = Minecraft.getInstance().player;
            if (playerAbort != null) {
                if (packet.missingBlankPatternFailure()) {
                    if (succeededBeforeAbort > 0) {
                        playerAbort.displayClientMessage(
                                Component.translatable("message.ae2utility.recipe_tree_upload_ok_compact", succeededBeforeAbort)
                                        .withStyle(ChatFormatting.GREEN),
                                false);
                    }
                    playerAbort.displayClientMessage(
                            Component.translatable("message.ae2utility.upload_failed_missing_blank_count", missingBlankTargetCount)
                                    .withStyle(ChatFormatting.YELLOW),
                            false);
                } else {
                    playerAbort.displayClientMessage(
                            Component.translatable("message.ae2utility.recipe_shift_batch_cancelled_notice").withStyle(ChatFormatting.GOLD),
                            false);
                }
            }
            JeiEncodeQueueDebugLog.warn("handleResult ABORT_REMAINING_BATCH patternNameRaw={} missingBlank={}",
                    packet.patternName(), packet.missingBlankPatternFailure());
            return;
        }
        if (packet.uploaded() && !packet.awaitingProviderCompletion()) {
            EaepProviderUiCloser.closeProviderSelectIfTop();
        }
        if (packet.awaitingProviderCompletion()) {
            awaitingProviderUploadCompletion = true;
            frozenUntilServerEncodeResponse = true;
            EaepUploadDebugLog.info("RecipeTreeUploadQueue entering awaiting-provider state inFlight={} pending={}",
                    IN_FLIGHT.size(), PENDING.size());
            JeiEncodeQueueDebugLog.info(
                    "handleResult AWAITING_PROVIDER uploaded={} patternName={} inFlightHeadRecipeId={} pendingLeft={} inFlightSize={}",
                    packet.uploaded(), packet.patternName(),
                    IN_FLIGHT.isEmpty() ? null : IN_FLIGHT.peekFirst().recipeId(),
                    PENDING.size(), IN_FLIGHT.size());
            Minecraft.getInstance().execute(() -> EaepProviderListRequest.trySendFromClient());
            return;
        }

        frozenUntilServerEncodeResponse = false;
        awaitingProviderUploadCompletion = false;
        EaepUploadDebugLog.info("RecipeTreeUploadQueue leaving awaiting-provider state uploaded={} inFlightBeforePoll={} pending={}",
                packet.uploaded(), IN_FLIGHT.size(), PENDING.size());

        EncodePatternPacket sentPacket = IN_FLIGHT.pollFirst();
        List<EncodePatternPacket> skippedSameEaep = List.of();
        if (packet.purgeRemainingQueuedSameEaepMachine() && sentPacket != null) {
            String nk = normalizedEaepKey(sentPacket.providerSearchKey());
            if (!nk.isEmpty()) {
                skippedSameEaep = drainPendingMatchingEaepKey(nk);
            }
        }

        String label = resultLabel(packet, sentPacket);
        if (!packet.uploaded()) {
            FAILED.add(label);
            if (packet.missingBlankPatternFailure()) {
                missingBlankFailCount++;
            }
        } else {
            SUCCEEDED.add(label);
        }

        if (!packet.uploaded() && sentPacket != null && shouldBookmarkFailedUpload(sentPacket)) {
            JeiBookmarkUtil.bookmarkRecipe(sentPacket.recipeId(),
                    sentPacket.outputs().isEmpty() ? null : sentPacket.outputs().getFirst());
        }
        receivedResults++;
        if (!skippedSameEaep.isEmpty()) {
            for (EncodePatternPacket p : skippedSameEaep) {
                FAILED.add(skippedEaepQueuedLabel(p));
                if (shouldBookmarkFailedUpload(p)) {
                    JeiBookmarkUtil.bookmarkRecipe(p.recipeId(),
                            p.outputs().isEmpty() ? null : p.outputs().getFirst());
                }
                receivedResults++;
            }
            JeiEncodeQueueDebugLog.warn("handleResult PURGE_SAME_EAEP_MACHINE count={} pendingLeftNow={}",
                    skippedSameEaep.size(), PENDING.size());
        }
        waitTicks = NEXT_PACKET_DELAY_TICKS;

        JeiEncodeQueueDebugLog.info(
                "handleResult FINAL uploaded={} patternNameRaw={} summaryLabel={} sentRecipeId={} pendingLeft={} results={}/{} purgeSameSkip={} waitTicks=>{}",
                packet.uploaded(), packet.patternName(), label, sentPacket != null ? sentPacket.recipeId() : null,
                PENDING.size(), receivedResults, expectedResults, skippedSameEaep.size(), waitTicks);

        showSummaryIfComplete();
    }

    private static boolean shouldBookmarkFailedUpload(EncodePatternPacket p) {
        if (p == null) {
            return false;
        }
        /*
         * JEI「当前页/整类」Ctrl+Shift 顺序批量：preserveInputOrder=false，失败时不再自动收藏配方（避免刷满 JEI 书签）。
         * 配方树批量上传：preserveInputOrder=true，仍保留收藏以便补做。
         */
        return !(p.jeiSequentialQueue() && p.shiftDown() && !p.preserveInputOrder());
    }

    private static String resultLabel(RecipeTreeUploadResultPacket packet, EncodePatternPacket sentPacket) {
        String raw = packet.patternName();
        if (raw != null && !raw.isBlank() && !"-".equals(raw)) {
            return raw;
        }
        if (sentPacket != null && sentPacket.recipeId() != null) {
            return sentPacket.recipeId().toString();
        }
        return "-";
    }

    private static void sendNext() {
        EncodePatternPacket next = PENDING.pollFirst();
        if (next == null) {
            waitTicks = 0;
            JeiEncodeQueueDebugLog.info(
                    "sendNext queue empty receivedResults={}/{} awaiting={} frozen={}",
                    receivedResults, expectedResults, awaitingProviderUploadCompletion, frozenUntilServerEncodeResponse);
            showSummaryIfComplete();
            return;
        }
        RecipeTreeUploadProgressState.setCurrent(next);
        IN_FLIGHT.addLast(next);
        PacketDistributor.sendToServer(next);
        frozenUntilServerEncodeResponse = true;
        JeiEncodeQueueDebugLog.info(
                "sendNext dispatched recipeId={} pendingLeft={} inFlightSize={} frozen=true sequentialFlag={}",
                next.recipeId(), PENDING.size(), IN_FLIGHT.size(), next.jeiSequentialQueue());
    }

    private static String normalizedEaepKey(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 从待发队列移除与给定 EAEP 供应器关键字（归一化后）匹配的条目。
     */
    private static List<EncodePatternPacket> drainPendingMatchingEaepKey(String normalizedRef) {
        if (normalizedRef.isEmpty()) {
            return List.of();
        }
        int pass = PENDING.size();
        List<EncodePatternPacket> drained = new ArrayList<>();
        for (int i = 0; i < pass; i++) {
            EncodePatternPacket cur = PENDING.pollFirst();
            if (cur == null) {
                break;
            }
            if (normalizedEaepKey(cur.providerSearchKey()).equals(normalizedRef)) {
                drained.add(cur);
            } else {
                PENDING.addLast(cur);
            }
        }
        return drained;
    }

    private static String skippedEaepQueuedLabel(EncodePatternPacket p) {
        if (p == null) {
            return "-";
        }
        if (p.patternName() != null && !p.patternName().isBlank()) {
            return p.patternName();
        }
        if (p.recipeId() != null) {
            return p.recipeId().toString();
        }
        return "-";
    }

    private static boolean isEaepProviderSelectScreen(Screen screen) {
        return screen != null && "com.extendedae_plus.client.screen.ProviderSelectScreen".equals(screen.getClass().getName());
    }

    private static void showSummaryIfComplete() {
        if (expectedResults <= 0 || receivedResults < expectedResults || !PENDING.isEmpty() || waitingForProviderScreen
                || waitTicks > 0 || frozenUntilServerEncodeResponse || awaitingProviderUploadCompletion) {
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player != null) {
            MutableComponent summary = uploadSummaryLines();
            player.displayClientMessage(summary, false);
        }
        JeiEncodeQueueDebugLog.info("showSummary COMPLETE ok={} fail={} missingBlankFails={}",
                joinNames(SUCCEEDED), joinNames(FAILED), missingBlankFailCount);
        clearSessionFully();
    }

    private static MutableComponent uploadSummaryLines() {
        int ok = SUCCEEDED.size();
        int fail = FAILED.size();
        int total = ok + fail;
        boolean allFailsAreMissingBlank =
                fail > 0 && missingBlankFailCount == fail;
        MutableComponent msg = Component.empty();
        msg.append(summarySuccessPart(ok, total));
        msg.append(summaryFailurePart(fail, total, allFailsAreMissingBlank));
        return msg;
    }

    private static MutableComponent summarySuccessPart(int okCount, int total) {
        if (total <= SUMMARY_DETAIL_NAME_THRESHOLD) {
            return Component.translatable("message.ae2utility.recipe_tree_upload_ok_detailed_intro")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(joinNames(SUCCEEDED)).withStyle(ChatFormatting.GREEN));
        }
        return Component.translatable("message.ae2utility.recipe_tree_upload_ok_compact", okCount)
                .withStyle(ChatFormatting.GREEN);
    }

    private static MutableComponent summaryFailurePart(int failCount, int total, boolean allFailsMissingBlank) {
        if (failCount <= 0) {
            return Component.empty();
        }
        MutableComponent suffix;
        if (total <= SUMMARY_DETAIL_NAME_THRESHOLD) {
            if (allFailsMissingBlank) {
                suffix = blankFailSummand(failCount);
            } else {
                suffix = Component.translatable("message.ae2utility.recipe_tree_upload_fail_detailed_intro")
                        .withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(joinNames(FAILED)).withStyle(ChatFormatting.YELLOW));
            }
        } else if (allFailsMissingBlank) {
            suffix = blankFailSummand(failCount);
        } else {
            suffix = Component.translatable("message.ae2utility.recipe_tree_upload_not_uploaded_compact", failCount)
                    .withStyle(ChatFormatting.YELLOW);
        }
        return suffix;
    }

    private static MutableComponent blankFailSummand(int failCount) {
        return Component.literal("，").append(Component.translatable("message.ae2utility.upload_failed_missing_blank_count", failCount)
                .withStyle(ChatFormatting.YELLOW));
    }

    private static String joinNames(List<String> names) {
        return names.isEmpty() ? "-" : String.join(",", names);
    }
}
