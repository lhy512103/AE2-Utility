package com.lhy.ae2utility.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.stacks.AEKey;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.IGrid;
import appeng.api.storage.MEStorage;
import appeng.api.storage.ITerminalHost;
import appeng.menu.SlotSemantics;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;

import net.minecraft.world.inventory.Slot;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.Ae2UtilityServerConfig;
import com.lhy.ae2utility.compat.WcwtCompat;
import com.lhy.ae2utility.debug.EaepUploadDebugLog;
import com.lhy.ae2utility.debug.JeiEncodeQueueDebugLog;
import com.lhy.ae2utility.init.ModDataComponents;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.InvalidateCraftableCachePacket;
import com.lhy.ae2utility.network.SyncEaepProviderSearchKeyPacket;
import com.lhy.ae2utility.util.EncodePatternInputChooser;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public final class EncodePatternService {
    private enum BlankPatternSource {
        PATTERN_TERMINAL_SLOT,
        PLAYER_INVENTORY,
        ME_NETWORK
    }

    private enum EncodeOutcome {
        SUCCESS,
        FAILURE,
        BATCH_SKIP_DUPLICATE,
        BATCH_ABORT_NO_BLANK,
        /** EAEP 供应器界面已打开；同刻继续处理会破坏待上传队列 */
        EAEP_PROVIDER_UI_OPENED
    }

    private record EncodeContext(MEStorage inventory, IActionSource actionSource, @Nullable IGrid grid) {}

    private record EncodeComputation(ItemStack encodedPattern, boolean canUploadToMatrix) {}

    private record EaepShiftBlankRefundHold(MEStorage inventory, IActionSource actionSource, BlankPatternSource source,
            AEItemKey blankKey) {}

    private static final ConcurrentHashMap<UUID, EaepShiftBlankRefundHold> EAEP_SHIFT_BLANK_PENDING = new ConcurrentHashMap<>();

    private EncodePatternService() {}

    public static void handle(Player player, EncodePatternPacket payload) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        boolean sequential = payload.jeiSequentialQueue();
        EncodeOutcome outcome = encodePatternInternal(serverPlayer, payload, sequential);
        if (sequential) {
            if (outcome == EncodeOutcome.BATCH_SKIP_DUPLICATE) {
                RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, sequentialResultLabel(payload), true);
            } else if (outcome == EncodeOutcome.BATCH_ABORT_NO_BLANK) {
                RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, sequentialResultLabel(payload), false, true,
                        false, true);
            }
        }
    }

    public static void handleBatch(ServerPlayer serverPlayer, List<EncodePatternPacket> patterns) {
        if (Ae2UtilityServerConfig.requireOpenPatternEncodingMenuForJei()
                && !serverPlayerHasOpenPatternEncodingLikeMenu(serverPlayer)) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.ae2utility.encode_rejected_require_open_encoding_menu"));
            return;
        }
        if (Ae2UtilityServerConfig.blockJeiFullCategoryBatchEncode()) {
            EncodePatternPacket anyFull =
                    patterns.stream().filter(EncodePatternPacket::jeiFullCategoryBatch).findFirst().orElse(null);
            if (anyFull != null) {
                EncodeBulkSessionLimiter.notifyBlockedFullJeCategoryBatch(serverPlayer,
                        anyFull.bulkEncodeSessionId());
                return;
            }
        }
        List<EncodePatternPacket> toProcess = patterns;
        int mx = Ae2UtilityServerConfig.jeiBulkEncodeMaxPatternsPerSession();
        if (mx > 0 && patterns.size() > mx) {
            toProcess = new ArrayList<>(patterns.subList(0, mx));
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.ae2utility.bulk_encode_truncated_to_server_batch", patterns.size(), mx)
                            .withStyle(net.minecraft.ChatFormatting.GOLD));
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
                            Component.translatable("message.ae2utility.batch_encode_eaep_deferred", remaining).withStyle(net.minecraft.ChatFormatting.GOLD));
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
                WirelessTerminalMenuHost<?> host = resolution.host();
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

    private static ItemStack encodeSmithingPatternFlexible(RecipeHolder<?> recipeHolder, EncodePatternPacket payload,
            List<GenericStack> in, List<GenericStack> out) {
        AEItemKey outKey = aeItemFromOutputSlot(out);
        if (outKey == null) {
            return ItemStack.EMPTY;
        }
        AEItemKey template =
                positionalSmithingSlot(in, 0);
        AEItemKey base =
                positionalSmithingSlot(in, 1);
        AEItemKey addition =
                positionalSmithingSlot(in, 2);
        ItemStack stacked = smithingEncodedIfComplete(recipeHolder, payload.substitute(), template, base, addition, outKey);
        if (!stacked.isEmpty()) {
            return stacked;
        }
        /*
         * JEI 锻造台分页有时槽位数为 2（无底材模板格）或非严格 [模板,基底,添加剂]；将「所有有效物品输入」按顺序再试一次。
         */
        List<AEItemKey> keys = flattenAeItemKeysPreserveOrder(in);
        if (keys.size() >= 3) {
            template = keys.get(0);
            base = keys.get(1);
            addition = keys.get(2);
        } else if (keys.size() == 2) {
            template = null;
            base = keys.getFirst();
            addition = keys.get(1);
        } else if (keys.size() == 1) {
            return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        return smithingEncodedIfComplete(recipeHolder, payload.substitute(), template, base, addition, outKey);
    }

    private static @Nullable AEItemKey positionalSmithingSlot(List<GenericStack> in, int index) {
        if (index < 0 || index >= in.size()) {
            return null;
        }
        GenericStack g = in.get(index);
        if (g == null || g.what() == null || !(g.what() instanceof AEItemKey itemKey)) {
            return null;
        }
        return itemKey;
    }

    private static @Nullable AEItemKey aeItemFromOutputSlot(List<GenericStack> out) {
        if (out.isEmpty() || out.getFirst() == null || out.getFirst().what() == null) {
            return null;
        }
        return out.getFirst().what() instanceof AEItemKey k ? k : null;
    }

    @SuppressWarnings("unchecked")
    private static ItemStack smithingEncodedIfComplete(RecipeHolder<?> recipeHolder, boolean substitute,
            @Nullable AEItemKey template,
            @Nullable AEItemKey base,
            @Nullable AEItemKey addition,
            AEItemKey outKey) {
        if (base == null || addition == null || outKey == null) {
            return ItemStack.EMPTY;
        }
        try {
            return PatternDetailsHelper.encodeSmithingTablePattern(
                    (RecipeHolder<SmithingRecipe>) recipeHolder, template, base, addition, outKey, substitute);
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    /** 每条 JEI 输入槽取「第一个」条目作为 AEItemKey（与编码发卡侧 pick 对齐），保持槽顺序列表。 */
    private static List<AEItemKey> flattenAeItemKeysPreserveOrder(List<GenericStack> in) {
        List<AEItemKey> keys = new ArrayList<>(4);
        for (GenericStack g : in) {
            if (g != null && g.what() instanceof AEItemKey k) {
                keys.add(k);
            }
        }
        return keys;
    }

    private static EncodeComputation computeEncodedPattern(ServerPlayer serverPlayer, EncodePatternPacket payload,
            List<GenericStack> in, List<GenericStack> out) {
        ItemStack encodedPattern = ItemStack.EMPTY;
        boolean canUploadToMatrix = false;

        int meaningfulInputCount = countMeaningfulInputs(in);

        if (payload.recipeId() != null) {
            var recipeHolder = serverPlayer.getServer().getRecipeManager().byKey(payload.recipeId()).orElse(null);
            if (recipeHolder != null) {
                if (recipeHolder.value() instanceof CraftingRecipe && meaningfulInputCount <= 9) {
                    ItemStack[] inArray = new ItemStack[9];
                    Arrays.fill(inArray, ItemStack.EMPTY);
                    var ingredients3x3 = appeng.util.CraftingRecipeUtil.ensure3by3CraftingMatrix(recipeHolder.value());

                    if (in.size() == 9) {
                        mapSparseCraftingInputs(in, inArray);
                    } else {
                        mapCompactCraftingInputs(in, ingredients3x3, inArray);
                    }

                    ItemStack outStack = out.isEmpty() || out.get(0) == null ? ItemStack.EMPTY : toItemStack(out.get(0));
                    if (outStack == null) {
                        outStack = ItemStack.EMPTY;
                    }
                    encodedPattern = PatternDetailsHelper.encodeCraftingPattern((RecipeHolder) recipeHolder, inArray, outStack,
                            payload.substitute(), payload.substituteFluids());
                    canUploadToMatrix = true;
                } else if (recipeHolder.value() instanceof SmithingRecipe) {
                    encodedPattern = encodeSmithingPatternFlexible((RecipeHolder<?>) recipeHolder, payload, in, out);
                    canUploadToMatrix = !encodedPattern.isEmpty();
                } else if (recipeHolder.value() instanceof StonecutterRecipe) {
                    AEItemKey inKey = in.isEmpty() || in.get(0) == null ? null : (in.get(0).what() instanceof AEItemKey k ? k : null);
                    AEItemKey outKey = out.isEmpty() || out.get(0) == null ? null : (out.get(0).what() instanceof AEItemKey k ? k : null);
                    if (inKey != null && outKey != null) {
                        encodedPattern = PatternDetailsHelper.encodeStonecuttingPattern((RecipeHolder) recipeHolder, inKey, outKey,
                                payload.substitute());
                        canUploadToMatrix = true;
                    }
                }
            }
        }

        if (encodedPattern.isEmpty()) {
            List<GenericStack> procIn = in.stream().filter(java.util.Objects::nonNull).toList();
            List<GenericStack> procOut = out.stream().filter(java.util.Objects::nonNull).toList();
            if (!procIn.isEmpty() && !procOut.isEmpty()) {
                encodedPattern = PatternDetailsHelper.encodeProcessingPattern(procIn, procOut);
            }
        }

        return new EncodeComputation(encodedPattern, canUploadToMatrix);
    }

    private static boolean eaepMatrixDuplicateAbortSingle(ServerPlayer serverPlayer, EncodePatternPacket payload,
            ItemStack encodedPattern, boolean canUploadToMatrix, @Nullable IGrid terminalGrid) {
        if (!payload.shiftDown() || !ModList.get().isLoaded("extendedae_plus")) {
            return false;
        }
        try {
            Class<?> pendingUtil = Class.forName("com.extendedae_plus.util.uploadPattern.CtrlQPendingUploadUtil");
            java.lang.reflect.Method findGrid = pendingUtil.getMethod("findPlayerGrid", ServerPlayer.class);
            IGrid eaepGrid = (IGrid) findGrid.invoke(null, serverPlayer);
            if (eaepGrid == null) {
                eaepGrid = terminalGrid;
            }
            if (eaepGrid == null) {
                return false;
            }
            Class<?> uploadUtil = Class.forName("com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil");
            try {
                java.lang.reflect.Method duplicateCheck = uploadUtil.getDeclaredMethod("matrixContainsPattern",
                        IGrid.class, ItemStack.class);
                duplicateCheck.setAccessible(true);
                boolean duplicate = (Boolean) duplicateCheck.invoke(null, eaepGrid, encodedPattern);
                if (duplicate) {
                    serverPlayer.sendSystemMessage(Component.translatable("extendedae_plus.message.matrix.duplicate"));
                    RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer,
                            payload.patternName().isBlank() ? sequentialResultLabel(payload) : payload.patternName(), false);
                    return true;
                }
            } catch (NoSuchMethodException ignored) {
            }
        } catch (Throwable ignored) {
        }
        return false;
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
                RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, sequentialResultLabel(payload), false);
            }
            return EncodeOutcome.FAILURE;
        }

        if (Ae2UtilityServerConfig.requireOpenPatternEncodingMenuForJei()
                && !serverPlayerHasOpenPatternEncodingLikeMenu(serverPlayer)) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.ae2utility.encode_rejected_require_open_encoding_menu"));
            if (payload.jeiSequentialQueue()) {
                RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, sequentialResultLabel(payload), false);
            } else {
                RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            }
            return EncodeOutcome.FAILURE;
        }

        if (EncodeBulkSessionLimiter.rejectIfLimited(serverPlayer, payload)) {
            return EncodeOutcome.FAILURE;
        }

        IActionSource actionSource = ctx.actionSource();
        MEStorage inventory = ctx.inventory();
        IGrid grid = ctx.grid();

        RecipeTreeUploadResultBridge.clearPendingName(serverPlayer);
        RecipeTreeUploadContextBridge.clear(serverPlayer);

        AEItemKey blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN);

        List<List<GenericStack>> inLists = payload.inputs();
        List<GenericStack> out = payload.outputs();

        List<GenericStack> in = new ArrayList<>();
        for (List<GenericStack> alts : inLists) {
            if (alts == null || alts.isEmpty()) {
                in.add(null);
            } else {
                in.add(EncodePatternInputChooser.pickEncodedInput(alts, inventory, payload.preserveInputOrder()));
            }
        }

        if (in.isEmpty() || out.isEmpty()) {
            RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            return EncodeOutcome.FAILURE;
        }

        EncodeComputation computation = computeEncodedPattern(serverPlayer, payload, in, out);
        ItemStack encodedPattern = computation.encodedPattern();
        boolean canUploadToMatrix = computation.canUploadToMatrix();

        if (encodedPattern.isEmpty()) {
            RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            return EncodeOutcome.FAILURE;
        }

        if (batchMode && EncodePatternDuplicateChecker.batchNetworkAlreadyContains(serverPlayer, grid, encodedPattern)) {
            JeiEncodeQueueDebugLog.info(
                    "encode batch skip duplicate player={} recipeId={} gridNull={} jeiSequentialQueue={}",
                    serverPlayer.getScoreboardName(), payload.recipeId(), grid == null, payload.jeiSequentialQueue());
            return EncodeOutcome.BATCH_SKIP_DUPLICATE;
        }

        if (!batchMode && eaepMatrixDuplicateAbortSingle(serverPlayer, payload, encodedPattern, canUploadToMatrix, grid)) {
            return EncodeOutcome.FAILURE;
        }

        BlankPatternSource blankSource = consumeOneBlankPattern(serverPlayer, inventory, actionSource, blankPatternKey);
        if (blankSource == null) {
            if (!batchMode) {
                serverPlayer.sendSystemMessage(Component.translatable("message.ae2utility.encode_failed_no_blank_named",
                        payload.patternName().isBlank() ? "-" : payload.patternName()));
                RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            }
            return batchMode ? EncodeOutcome.BATCH_ABORT_NO_BLANK : EncodeOutcome.FAILURE;
        }

        try {
            rememberProviderSearchKey(serverPlayer, payload, encodedPattern);

            if (payload.shiftDown() && ModList.get().isLoaded("extendedae_plus")) {
                try {
                    Class<?> pendingUtil = Class.forName("com.extendedae_plus.util.uploadPattern.CtrlQPendingUploadUtil");
                    java.lang.reflect.Method findGrid = pendingUtil.getMethod("findPlayerGrid", ServerPlayer.class);
                    IGrid eaepGrid = (IGrid) findGrid.invoke(null, serverPlayer);
                    if (eaepGrid == null) {
                        eaepGrid = grid;
                    }

                    EaepUploadDebugLog.info(
                            "EncodePattern shift EAEP start player={} patternName={} recipeId={} canMatrixHint={} eaepGridNull={}",
                            serverPlayer.getScoreboardName(), payload.patternName(), payload.recipeId(), canUploadToMatrix,
                            eaepGrid == null);

                        if (eaepGrid == null) {
                            if (payload.jeiSequentialQueue()) {
                                refundBlankPattern(serverPlayer, inventory, actionSource, blankPatternKey, blankSource);
                                serverPlayer.sendSystemMessage(
                                        Component.translatable("message.ae2utility.recipe_shift_batch_aborted_no_network")
                                                .withStyle(net.minecraft.ChatFormatting.RED));
                                RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, sequentialResultLabel(payload), false,
                                        true);
                                return EncodeOutcome.FAILURE;
                            }
                    } else {
                        /*
                         * 与终端 Alt+上传 一样走「装配矩阵快速通道」：EAEP 的 uploadPatternToMatrix 可接受处理样板等，
                         * 矩阵与直插均失败时再走供应器待定队列。
                         */
                        Class<?> uploadUtil = Class.forName("com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil");
                        java.lang.reflect.Method uploadToMatrix = uploadUtil.getMethod("uploadPatternToMatrix", ServerPlayer.class,
                                ItemStack.class, IGrid.class);
                        boolean uploadedMatrix = (Boolean) uploadToMatrix.invoke(null, serverPlayer, encodedPattern, eaepGrid);
                        EaepUploadDebugLog.info(
                                "EncodePattern matrix upload attempted recipeId={} canMatrixHint={} uploaded={}",
                                payload.recipeId(), canUploadToMatrix, uploadedMatrix);

                        if (uploadedMatrix) {
                            disarmEaepShiftBlankRefund(serverPlayer);
                            String okName = payload.patternName().isBlank() ? sequentialResultLabel(payload) : payload.patternName();
                            RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, okName, true);
                            sendCraftableCacheRefreshIfNonEmpty(serverPlayer, payload);
                            return EncodeOutcome.SUCCESS;
                        }

                        if (InventoryPatternMatrixUploadService.tryDirectMatrixInsert(serverPlayer, encodedPattern, eaepGrid)) {
                            EaepUploadDebugLog.info(
                                    "EncodePattern matrix direct insert ok (fallback) recipeId={}",
                                    payload.recipeId());
                            disarmEaepShiftBlankRefund(serverPlayer);
                            String okName = payload.patternName().isBlank() ? sequentialResultLabel(payload) : payload.patternName();
                            RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, okName, true);
                            sendCraftableCacheRefreshIfNonEmpty(serverPlayer, payload);
                            return EncodeOutcome.SUCCESS;
                        }

                        /*
                         * JEI Ctrl+Shift 顺序批量：preserveInputOrder=false（配方树批量为 true）。
                         * 矩阵与 EAEP upload 均未接受时视作「矩阵满/无法再塞」一类，中止整批，避免逐项弹供应器又把失败配方收藏满 JEI。
                         */
                        boolean jeiSequentialShiftJeBulkOnly = payload.jeiSequentialQueue()
                                && payload.shiftDown()
                                && !payload.preserveInputOrder();
                        if (jeiSequentialShiftJeBulkOnly) {
                            disarmEaepShiftBlankRefund(serverPlayer);
                            giveEncodedPatternNoUpload(serverPlayer, encodedPattern);
                            serverPlayer.sendSystemMessage(
                                    Component.translatable("message.ae2utility.shift_batch_aborted_matrix_reject").withStyle(
                                            net.minecraft.ChatFormatting.GOLD));
                            RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, sequentialResultLabel(payload), false, true);
                            sendCraftableCacheRefreshIfNonEmpty(serverPlayer, payload);
                            return EncodeOutcome.FAILURE;
                        }

                        sendEaepProviderSearchSync(serverPlayer, payload);
                        RecipeTreeUploadContextBridge.rememberGrid(serverPlayer, eaepGrid);
                        RecipeTreeUploadContextBridge.rememberPendingSearchKey(serverPlayer,
                                deriveRawEaepSearchKeyForSync(serverPlayer, payload));
                        RecipeTreeUploadContextBridge.rememberPendingProviderDisplayName(serverPlayer, payload.providerDisplayName());
                        java.lang.reflect.Method clearPending = pendingUtil.getMethod("clearPendingCtrlQUpload", ServerPlayer.class);
                        clearPending.invoke(null, serverPlayer);
                        RecipeTreeUploadResultBridge.rememberPendingName(serverPlayer, sequentialResultLabel(payload));
                        List<AEKey> pendingCraftableRefresh = collectCraftableKeysForRefresh(payload);
                        if (!pendingCraftableRefresh.isEmpty()) {
                            RecipeTreeUploadResultBridge.rememberPendingCraftableRefresh(serverPlayer, pendingCraftableRefresh);
                        }
                        java.lang.reflect.Method beginUpload = pendingUtil.getMethod("beginPendingCtrlQUpload", ServerPlayer.class,
                                ItemStack.class);
                        beginUpload.invoke(null, serverPlayer, encodedPattern.copyWithCount(1));

                        Long rememberedProviderId = RecipeTreeUploadContextBridge.getRememberedProviderId(serverPlayer,
                                RecipeTreeUploadContextBridge.getPendingSearchKey(serverPlayer),
                                payload.providerDisplayName());
                        if (rememberedProviderId != null) {
                            java.lang.reflect.Method uploadPending = pendingUtil.getMethod("uploadPendingCtrlQPattern",
                                    ServerPlayer.class,
                                    long.class);
                            boolean reusedUploadOk =
                                    (Boolean) uploadPending.invoke(null, serverPlayer, rememberedProviderId.longValue());
                            EaepUploadDebugLog.info(
                                    "EncodePattern EAEP reused providerId={} recipeId={} uploaded={}",
                                    rememberedProviderId, payload.recipeId(), reusedUploadOk);
                            if (reusedUploadOk) {
                                disarmEaepShiftBlankRefund(serverPlayer);
                                sendCraftableCacheRefreshIfNonEmpty(serverPlayer, payload);
                                return EncodeOutcome.SUCCESS;
                            }
                            RecipeTreeUploadContextBridge.forgetRememberedProvider(serverPlayer,
                                    RecipeTreeUploadContextBridge.getPendingSearchKey(serverPlayer),
                                    payload.providerDisplayName());
                        }

                        if (!batchMode || payload.jeiSequentialQueue()) {
                            armEaepShiftBlankForPendingProvider(serverPlayer, ctx, blankSource, blankPatternKey);
                            RecipeTreeUploadResultBridge.sendAwaitingProviderUpload(serverPlayer, sequentialResultLabel(payload));
                        }
                        EaepUploadDebugLog.info(
                                "EncodePattern EAEP beginPendingCtrlQUpload sequential={} recipeId={} patternItem={} rememberedGrid=true",
                                payload.jeiSequentialQueue(), payload.recipeId(), encodedPattern.getItem());
                        return EncodeOutcome.EAEP_PROVIDER_UI_OPENED;
                    }
                } catch (Throwable e) {
                    RecipeTreeUploadResultBridge.clearPendingName(serverPlayer);
                    boolean refundedShiftBlank = refundEaepShiftBlankIfPending(serverPlayer);
                    EaepUploadDebugLog.error("EncodePattern EAEP branch threw patternName=" + payload.patternName(), e);
                    String failName = payload.jeiSequentialQueue() ? sequentialResultLabel(payload) : payload.patternName();
                    RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, failName, false);
                    if (!refundedShiftBlank) {
                        refundBlankPattern(serverPlayer, inventory, actionSource, blankPatternKey, blankSource);
                    }
                    return EncodeOutcome.FAILURE;
                }
            }
            giveEncodedPatternNoUpload(serverPlayer, encodedPattern);
            disarmEaepShiftBlankRefund(serverPlayer);
            RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            sendCraftableCacheRefreshIfNonEmpty(serverPlayer, payload);
            return EncodeOutcome.SUCCESS;
        } catch (Throwable e) {
            RecipeTreeUploadResultBridge.clearPendingName(serverPlayer);
            Ae2UtilityMod.LOGGER.error("Error encoding pattern: ", e);
            RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            if (!refundEaepShiftBlankIfPending(serverPlayer)) {
                refundBlankPattern(serverPlayer, inventory, actionSource, blankPatternKey, blankSource);
            }
            return EncodeOutcome.FAILURE;
        }
    }

    /**
     * @return 消耗成功时的来源；无法消耗则返回 null
     */
    private static @Nullable BlankPatternSource consumeOneBlankPattern(ServerPlayer serverPlayer, MEStorage inventory,
            IActionSource actionSource, AEItemKey blankPatternKey) {
        if (serverPlayer.containerMenu instanceof PatternEncodingTermMenu patternMenu) {
            for (Slot slot : patternMenu.getSlots(SlotSemantics.BLANK_PATTERN)) {
                ItemStack stack = slot.getItem();
                if (AEItems.BLANK_PATTERN.is(stack) && !stack.isEmpty()) {
                    stack.shrink(1);
                    if (stack.isEmpty()) {
                        slot.set(ItemStack.EMPTY);
                    } else {
                        slot.set(stack);
                    }
                    slot.setChanged();
                    patternMenu.broadcastChanges();
                    return BlankPatternSource.PATTERN_TERMINAL_SLOT;
                }
            }
        } else if (WcwtCompat.isWcwtMenu(serverPlayer.containerMenu)) {
            for (Slot slot : serverPlayer.containerMenu.slots) {
                ItemStack stack = slot.getItem();
                if (AEItems.BLANK_PATTERN.is(stack) && !stack.isEmpty()) {
                    stack.shrink(1);
                    if (stack.isEmpty()) {
                        slot.set(ItemStack.EMPTY);
                    } else {
                        slot.set(stack);
                    }
                    slot.setChanged();
                    serverPlayer.containerMenu.broadcastChanges();
                    return BlankPatternSource.PATTERN_TERMINAL_SLOT;
                }
            }
        }

        for (int i = 0; i < serverPlayer.getInventory().getContainerSize(); i++) {
            ItemStack stack = serverPlayer.getInventory().getItem(i);
            if (AEItems.BLANK_PATTERN.is(stack) && !stack.isEmpty()) {
                stack.shrink(1);
                serverPlayer.getInventory().setChanged();
                return BlankPatternSource.PLAYER_INVENTORY;
            }
        }

        long extracted = inventory.extract(blankPatternKey, 1, Actionable.MODULATE, actionSource);
        if (extracted > 0) {
            return BlankPatternSource.ME_NETWORK;
        }
        return null;
    }

    private static void sendCraftableCacheRefreshIfNonEmpty(ServerPlayer serverPlayer, EncodePatternPacket payload) {
        List<AEKey> keys = collectCraftableKeysForRefresh(payload);
        if (!keys.isEmpty()) {
            PacketDistributor.sendToPlayer(serverPlayer, new InvalidateCraftableCachePacket(keys));
        }
    }

    /**
     * 与发包时写入的 {@link EncodePatternPacket#providerSearchKey()} 一致逻辑的「原始」关键字（再走 EAEP {@code resolveSearchKeyAlias}）。
     */
    private static String deriveRawEaepSearchKeyForSync(ServerPlayer serverPlayer, EncodePatternPacket payload) {
        String searchKey = payload.providerSearchKey();
        if (searchKey != null && !searchKey.isBlank()) {
            return searchKey;
        }
        String display = payload.providerDisplayName();
        if (display != null && !display.isBlank()) {
            return display;
        }
        if (payload.recipeId() == null) {
            return "";
        }

        var recipeHolder = serverPlayer.getServer().getRecipeManager().byKey(payload.recipeId()).orElse(null);
        if (recipeHolder == null) {
            return "";
        }

        if (recipeHolder.value() instanceof CraftingRecipe) {
            return "crafting";
        }

        try {
            Class<?> uploadUtil = Class.forName("com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil");
            java.lang.reflect.Method mapRecipe = uploadUtil.getMethod("mapRecipeTypeToSearchKey",
                    net.minecraft.world.item.crafting.Recipe.class);
            Object mapped = mapRecipe.invoke(null, recipeHolder.value());
            if (mapped instanceof String mappedString && !mappedString.isBlank()) {
                return mappedString;
            }
        } catch (Throwable ignored) {
        }

        ResourceLocation recipeTypeId = net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(recipeHolder.value().getType());
        return recipeTypeId != null ? recipeTypeId.getPath() : "";
    }

    private static void sendEaepProviderSearchSync(ServerPlayer player, EncodePatternPacket payload) {
        String packetRaw = payload.providerSearchKey();
        boolean craftingNeedsPreset = payload.recipeId() != null && player.getServer() != null
                && player.getServer().getRecipeManager().byKey(payload.recipeId())
                .map(h -> h.value() instanceof CraftingRecipe).orElse(false)
                && (packetRaw == null || packetRaw.isBlank());

        if (craftingNeedsPreset) {
            PacketDistributor.sendToPlayer(player, new SyncEaepProviderSearchKeyPacket(true, ""));
            return;
        }

        PacketDistributor.sendToPlayer(player, new SyncEaepProviderSearchKeyPacket(false, deriveRawEaepSearchKeyForSync(player, payload)));
    }

    private static void rememberProviderSearchKey(ServerPlayer serverPlayer, EncodePatternPacket payload, ItemStack encodedPattern) {
        String searchKey = resolveProviderSearchKey(serverPlayer, payload, null);
        if (searchKey != null && !searchKey.isBlank()) {
            encodedPattern.set(ModDataComponents.PATTERN_PROVIDER_SEARCH_KEY.get(), searchKey);
        }
    }

    private static String resolveProviderSearchKey(ServerPlayer serverPlayer, EncodePatternPacket payload, @Nullable Class<?> uploadUtilClass) {
        String searchKey = payload.providerSearchKey();
        if (searchKey != null && !searchKey.isBlank()) {
            return resolveAlias(uploadUtilClass, searchKey);
        }
        if (payload.recipeId() == null) {
            return "";
        }

        var recipeHolder = serverPlayer.getServer().getRecipeManager().byKey(payload.recipeId()).orElse(null);
        if (recipeHolder == null) {
            return "";
        }

        if (recipeHolder.value() instanceof CraftingRecipe) {
            return resolveAlias(uploadUtilClass, "crafting");
        }

        if (uploadUtilClass != null) {
            try {
                java.lang.reflect.Method mapRecipe = uploadUtilClass.getMethod("mapRecipeTypeToSearchKey",
                        net.minecraft.world.item.crafting.Recipe.class);
                Object mapped = mapRecipe.invoke(null, recipeHolder.value());
                if (mapped instanceof String mappedString && !mappedString.isBlank()) {
                    return mappedString;
                }
            } catch (Throwable ignored) {
            }
        }

        ResourceLocation recipeTypeId = net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(recipeHolder.value().getType());
        if (recipeTypeId != null) {
            return recipeTypeId.getPath();
        }

        return "";
    }

    private static String resolveAlias(@Nullable Class<?> uploadUtilClass, String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return "";
        }
        if (uploadUtilClass != null) {
            try {
                java.lang.reflect.Method resolveAlias = uploadUtilClass.getMethod("resolveSearchKeyAlias", String.class);
                Object resolved = resolveAlias.invoke(null, rawKey);
                if (resolved instanceof String resolvedString && !resolvedString.isBlank()) {
                    return resolvedString;
                }
            } catch (Throwable ignored) {
            }
        }
        return rawKey;
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

    private static void refundBlankPattern(ServerPlayer player, MEStorage inventory, IActionSource actionSource,
            AEItemKey blankPatternKey, BlankPatternSource source) {
        switch (source) {
            case PLAYER_INVENTORY -> ItemHandlerHelper.giveItemToPlayer(player, AEItems.BLANK_PATTERN.stack());
            case ME_NETWORK -> {
                long inserted = inventory.insert(blankPatternKey, 1, Actionable.MODULATE, actionSource);
                if (inserted <= 0) {
                    ItemHandlerHelper.giveItemToPlayer(player, AEItems.BLANK_PATTERN.stack());
                }
            }
            case PATTERN_TERMINAL_SLOT -> {
                if (player.containerMenu instanceof PatternEncodingTermMenu menu) {
                    for (Slot slot : menu.getSlots(SlotSemantics.BLANK_PATTERN)) {
                        ItemStack cur = slot.getItem();
                        if (cur.isEmpty()) {
                            slot.set(AEItems.BLANK_PATTERN.stack());
                            slot.setChanged();
                            menu.broadcastChanges();
                            return;
                        }
                        if (AEItems.BLANK_PATTERN.is(cur) && cur.getCount() < cur.getMaxStackSize()) {
                            cur.grow(1);
                            slot.set(cur);
                            slot.setChanged();
                            menu.broadcastChanges();
                            return;
                        }
                    }
                } else if (WcwtCompat.isWcwtMenu(player.containerMenu)) {
                    for (Slot slot : player.containerMenu.slots) {
                        ItemStack cur = slot.getItem();
                        if (cur.isEmpty()) {
                            slot.set(AEItems.BLANK_PATTERN.stack());
                            slot.setChanged();
                            player.containerMenu.broadcastChanges();
                            return;
                        }
                        if (AEItems.BLANK_PATTERN.is(cur) && cur.getCount() < cur.getMaxStackSize()) {
                            cur.grow(1);
                            slot.set(cur);
                            slot.setChanged();
                            player.containerMenu.broadcastChanges();
                            return;
                        }
                    }
                }
                ItemHandlerHelper.giveItemToPlayer(player, AEItems.BLANK_PATTERN.stack());
            }
        }
    }

    private static void mapSparseCraftingInputs(List<GenericStack> inputStacks, ItemStack[] inArray) {
        for (int i = 0; i < Math.min(9, inputStacks.size()); i++) {
            inArray[i] = toItemStack(inputStacks.get(i));
        }
    }

    private static int countMeaningfulInputs(List<GenericStack> inputStacks) {
        int count = 0;
        for (GenericStack inputStack : inputStacks) {
            if (inputStack != null && inputStack.what() != null && inputStack.amount() > 0) {
                count++;
            }
        }
        return count;
    }

    private static void mapCompactCraftingInputs(List<GenericStack> inputStacks, List<Ingredient> ingredients3x3,
            ItemStack[] inArray) {
        int jeiInputIndex = 0;
        for (int i = 0; i < 9; i++) {
            if (!ingredients3x3.get(i).isEmpty() && jeiInputIndex < inputStacks.size()) {
                inArray[i] = toItemStack(inputStacks.get(jeiInputIndex++));
            }
        }
    }

    private static ItemStack toItemStack(GenericStack stack) {
        if (stack == null) {
            return ItemStack.EMPTY;
        }
        if (stack.what() instanceof AEItemKey itemKey) {
            int count = (int) Math.max(1, Math.min(Integer.MAX_VALUE, stack.amount()));
            return itemKey.toStack(count);
        }

        ItemStack inputStack = GenericStack.wrapInItemStack(stack);
        return inputStack == null ? ItemStack.EMPTY : inputStack;
    }

    public static void disarmEaepShiftBlankRefund(ServerPlayer player) {
        if (player != null) {
            EAEP_SHIFT_BLANK_PENDING.remove(player.getUUID());
        }
    }

    /**
     * 已进入 EAEP「等供应器」路径后记录空白样板来源，{@link RecipeTreeUploadResultBridge#flushPendingResult} 成功时卸下、失败则退还。
     */
    private static void armEaepShiftBlankForPendingProvider(ServerPlayer player, EncodeContext ctx, BlankPatternSource source,
            AEItemKey blankKey) {
        if (player != null) {
            EAEP_SHIFT_BLANK_PENDING.put(player.getUUID(), new EaepShiftBlankRefundHold(ctx.inventory(), ctx.actionSource(), source, blankKey));
        }
    }

    /** @return 若已对 EAEP 顺序队列挂载过空白退款则退还并移除记录 */
    public static boolean refundEaepShiftBlankIfPending(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        EaepShiftBlankRefundHold hold = EAEP_SHIFT_BLANK_PENDING.remove(player.getUUID());
        if (hold == null) {
            return false;
        }
        refundBlankPattern(player, hold.inventory(), hold.actionSource(), hold.blankKey(), hold.source());
        return true;
    }
}
