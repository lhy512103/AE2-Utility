package com.lhy.ae2utility.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
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
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
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
import com.lhy.ae2utility.api.pattern.PatternEncodingBatch;
import com.lhy.ae2utility.api.pattern.PatternEncodingRequest;
import com.lhy.ae2utility.api.pattern.PatternUploadMode;
import com.lhy.ae2utility.compat.WcwtCompat;
import com.lhy.ae2utility.debug.EaepUploadDebugLog;
import com.lhy.ae2utility.debug.JeiEncodeQueueDebugLog;
import com.lhy.ae2utility.init.ModDataComponents;
import com.lhy.ae2utility.integration.eaep.EaepDirectCompat;
import com.lhy.ae2utility.integration.eaep.EaepReflection;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.EncodePreviewResultPacket;
import com.lhy.ae2utility.network.InvalidateCraftableCachePacket;
import com.lhy.ae2utility.network.NetworkValidation;
import com.lhy.ae2utility.network.QueryEncodePreviewPacket;
import com.lhy.ae2utility.network.SyncEaepProviderSearchKeyPacket;
import com.lhy.ae2utility.util.EncodePatternInputChooser;
import com.lhy.ae2utility.util.PatternEncodingPreview;

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

    private record EncodeComputation(ItemStack encodedPattern, boolean canUploadToMatrix,
            List<GenericStack> encodedInputs) {}

    private record EaepShiftBlankRefundHold(MEStorage inventory, IActionSource actionSource, BlankPatternSource source,
            AEItemKey blankKey) {}

    private static final ConcurrentHashMap<UUID, EaepShiftBlankRefundHold> EAEP_SHIFT_BLANK_PENDING = new ConcurrentHashMap<>();

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
        PacketDistributor.sendToPlayer(serverPlayer, result);
    }

    private static EncodePreviewResultPacket previewEncodedPattern(ServerPlayer serverPlayer, int requestId,
            EncodePatternPacket payload) {
        EncodeContext ctx = resolveEncodeContext(serverPlayer);
        if (ctx == null) {
            return EncodePreviewResultPacket.empty(requestId);
        }
        if (Ae2UtilityServerConfig.requireOpenPatternEncodingMenuForJei()
                && !serverPlayerHasOpenPatternEncodingLikeMenu(serverPlayer)) {
            return EncodePreviewResultPacket.empty(requestId);
        }
        List<List<GenericStack>> inLists = payload.inputs();
        List<GenericStack> out = payload.outputs();
        if (!isValidPatternPayload(inLists, out)) {
            return EncodePreviewResultPacket.empty(requestId);
        }
        java.util.function.Predicate<AEKey> craftablePredicate =
                ctx.grid() != null ? key -> ctx.grid().getCraftingService().isCraftable(key) : null;
        Set<AEKey> outputKeys = out.stream()
                .filter(java.util.Objects::nonNull)
                .map(GenericStack::what)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<GenericStack> in = new ArrayList<>();
        for (List<GenericStack> alts : inLists) {
            if (alts == null || alts.isEmpty()) {
                in.add(null);
            } else {
                GenericStack chosen = EncodePatternInputChooser.pickEncodedInput(alts, ctx.inventory(), craftablePredicate,
                        payload.preserveInputOrder(), outputKeys::contains);
                if (chosen == null) {
                    return EncodePreviewResultPacket.empty(requestId);
                }
                in.add(chosen);
            }
        }
        if (in.isEmpty() || out.isEmpty()) {
            return EncodePreviewResultPacket.empty(requestId);
        }
        EncodeComputation computation = computeEncodedPattern(serverPlayer, payload, in, out, ctx.inventory(),
                craftablePredicate);
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
    private static RecipeHolder<CraftingRecipe> asCraftingHolder(RecipeHolder<?> recipeHolder) {
        return (RecipeHolder<CraftingRecipe>) recipeHolder;
    }

    @SuppressWarnings("unchecked")
    private static RecipeHolder<StonecutterRecipe> asStonecutterHolder(RecipeHolder<?> recipeHolder) {
        return (RecipeHolder<StonecutterRecipe>) recipeHolder;
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
            List<GenericStack> in, List<GenericStack> out, @Nullable MEStorage inventory,
            @Nullable java.util.function.Predicate<AEKey> craftable) {
        ItemStack encodedPattern = ItemStack.EMPTY;
        boolean canUploadToMatrix = false;
        List<GenericStack> encodedInputs = List.of();
        // 一旦把配方识别为合成/锻造/切石这类「结构化」配方，就绝不能再静默回退成处理样板，
        // 否则会出现「合成样板被写成处理样板」（原版 AE 编码终端不会这样）。
        boolean recognizedStructured = false;

        int meaningfulInputCount = countMeaningfulInputs(in);
        Set<AEKey> outputKeys = out.stream()
                .filter(java.util.Objects::nonNull)
                .map(GenericStack::what)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        if (payload.recipeId() != null) {
            var recipeHolder = serverPlayer.getServer().getRecipeManager().byKey(payload.recipeId()).orElse(null);
            if (recipeHolder != null) {
                if (recipeHolder.value() instanceof CraftingRecipe craftingRecipe && meaningfulInputCount <= 9
                        && craftingRecipe.canCraftInDimensions(3, 3)) {
                    recognizedStructured = true;
                    // 对齐原版 AE2 EncodingHelper#encodeCraftingRecipe：按配方自身的 3x3 原料，从网络匹配项里选材。
                    ItemStack[] inArray = EncodePatternInputChooser.buildCraftingGridFromRecipe(
                            craftingRecipe, inventory, craftable, outputKeys, serverPlayer.getInventory().items);

                    ItemStack outStack = out.isEmpty() || out.get(0) == null ? ItemStack.EMPTY : EncodePatternInputChooser.toItemStack(out.get(0));
                    if (outStack == null) {
                        outStack = ItemStack.EMPTY;
                    }
                    encodedPattern = PatternDetailsHelper.encodeCraftingPattern(asCraftingHolder(recipeHolder), inArray, outStack,
                            payload.substitute(), payload.substituteFluids());
                    encodedInputs = EncodePatternInputChooser.alignCraftingGridToJeiSlots(in, inArray);
                    canUploadToMatrix = true;
                } else if (recipeHolder.value() instanceof SmithingRecipe) {
                    recognizedStructured = true;
                    encodedPattern = encodeSmithingPatternFlexible((RecipeHolder<?>) recipeHolder, payload, in, out);
                    encodedInputs = in;
                    canUploadToMatrix = !encodedPattern.isEmpty();
                } else if (recipeHolder.value() instanceof StonecutterRecipe) {
                    recognizedStructured = true;
                    AEItemKey inKey = in.isEmpty() || in.get(0) == null ? null : (in.get(0).what() instanceof AEItemKey k ? k : null);
                    AEItemKey outKey = out.isEmpty() || out.get(0) == null ? null : (out.get(0).what() instanceof AEItemKey k ? k : null);
                    if (inKey != null && outKey != null) {
                        encodedPattern = PatternDetailsHelper.encodeStonecuttingPattern(asStonecutterHolder(recipeHolder), inKey, outKey,
                                payload.substitute());
                        encodedInputs = in;
                        canUploadToMatrix = true;
                    }
                }
            }
        }

        if (encodedPattern.isEmpty() && payload.craftingCategoryHint() && meaningfulInputCount <= 9) {
            CraftingFallbackMatch fallback = findCraftingRecipeFromJeiInputs(serverPlayer, in);
            if (fallback != null) {
                // 仅在真正反查到 3x3 合成配方时才视为结构化合成；否则保持 false，让其按处理样板编码，
                // 避免 Create 动力合成器这类「JEI 看似合成、实则放不进 3x3」的配方点击后无任何样板产出。
                recognizedStructured = true;
                ItemStack outStack = out.isEmpty() || out.get(0) == null ? ItemStack.EMPTY : EncodePatternInputChooser.toItemStack(out.get(0));
                if (outStack == null) {
                    outStack = ItemStack.EMPTY;
                }
                encodedPattern = PatternDetailsHelper.encodeCraftingPattern(fallback.recipeHolder(),
                        fallback.inputs(), outStack, payload.substitute(), payload.substituteFluids());
                encodedInputs = EncodePatternInputChooser.alignCraftingGridToJeiSlots(in, fallback.inputs());
                canUploadToMatrix = true;
            }
        }

        // 仅当配方未被识别为结构化配方时才作为处理样板编码；结构化配方若编码失败则保持空（上层按失败处理），
        // 绝不把合成/锻造/切石样板悄悄写成处理样板。
        if (encodedPattern.isEmpty() && !recognizedStructured) {
            List<GenericStack> procIn = in.stream().filter(java.util.Objects::nonNull).toList();
            List<GenericStack> procOut = out.stream().filter(java.util.Objects::nonNull).toList();
            if (!procIn.isEmpty() && !procOut.isEmpty()) {
                encodedPattern = PatternDetailsHelper.encodeProcessingPattern(procIn, procOut);
                encodedInputs = in;
            }
        }

        if (encodedPattern.isEmpty()) {
            return new EncodeComputation(encodedPattern, canUploadToMatrix, List.of());
        }
        return new EncodeComputation(encodedPattern, canUploadToMatrix, encodedInputs);
    }

    private record CraftingFallbackMatch(RecipeHolder<CraftingRecipe> recipeHolder, ItemStack[] inputs) {}

    /**
     * JEI 合成类别里可能出现“客户端 JEI 合成出来的 recipeId”（例如 Occultism 的已绑定束缚之书变体），
     * 服务端 RecipeManager.byKey 查不到这个 id。此时用 JEI 发来的实际 3x3 物品反查真实合成配方，并保留
     * 这些实际输入（含 NBT）写入样板。
     */
    @SuppressWarnings("unchecked")
    private static @Nullable CraftingFallbackMatch findCraftingRecipeFromJeiInputs(ServerPlayer serverPlayer,
            List<GenericStack> in) {
        ItemStack[] sparseInputs = buildSparseCraftingInputsFromJei(in);
        CraftingInput sparseInput = CraftingInput.of(3, 3, Arrays.asList(sparseInputs));
        var sparseMatch = serverPlayer.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, sparseInput, serverPlayer.level());
        if (sparseMatch.isPresent()) {
            return new CraftingFallbackMatch((RecipeHolder<CraftingRecipe>) (RecipeHolder<?>) sparseMatch.get(), sparseInputs);
        }

        ItemStack[] compactInputs = buildCompactCraftingInputsFromJei(in);
        if (Arrays.equals(sparseInputs, compactInputs)) {
            return null;
        }
        CraftingInput compactInput = CraftingInput.of(3, 3, Arrays.asList(compactInputs));
        var compactMatch = serverPlayer.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, compactInput, serverPlayer.level());
        return compactMatch
                .map(holder -> new CraftingFallbackMatch((RecipeHolder<CraftingRecipe>) (RecipeHolder<?>) holder, compactInputs))
                .orElse(null);
    }

    private static ItemStack[] buildSparseCraftingInputsFromJei(List<GenericStack> in) {
        ItemStack[] inArray = new ItemStack[9];
        Arrays.fill(inArray, ItemStack.EMPTY);
        for (int i = 0; i < Math.min(9, in.size()); i++) {
            inArray[i] = EncodePatternInputChooser.toItemStack(in.get(i));
        }
        return inArray;
    }

    private static ItemStack[] buildCompactCraftingInputsFromJei(List<GenericStack> in) {
        ItemStack[] inArray = new ItemStack[9];
        Arrays.fill(inArray, ItemStack.EMPTY);
        int outIndex = 0;
        for (GenericStack stack : in) {
            if (outIndex >= 9) {
                break;
            }
            ItemStack itemStack = EncodePatternInputChooser.toItemStack(stack);
            if (!itemStack.isEmpty()) {
                inArray[outIndex++] = itemStack;
            }
        }
        return inArray;
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
        IGrid eaepGrid = com.lhy.ae2utility.integration.eaep.EaepReflection.findPlayerGrid(serverPlayer);
        if (eaepGrid == null) {
            eaepGrid = terminalGrid;
        }
        if (eaepGrid == null) {
            return false;
        }
        if (com.lhy.ae2utility.integration.eaep.EaepReflection.matrixContainsPattern(eaepGrid, encodedPattern)) {
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
                || !com.lhy.ae2utility.integration.eco.EcoReflection.isLoaded()) {
            return false;
        }
        if (!com.lhy.ae2utility.integration.eco.EcoReflection.containsPattern(terminalGrid, encodedPattern)) {
            return false;
        }
        serverPlayer.sendSystemMessage(
                Component.translatable("message.ae2utility.eco_pattern_duplicate").withStyle(net.minecraft.ChatFormatting.GOLD));
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

        AEItemKey blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN);

        List<List<GenericStack>> inLists = payload.inputs();
        List<GenericStack> out = payload.outputs();
        if (!isValidPatternPayload(inLists, out)) {
            serverPlayer.sendSystemMessage(Component.translatable("message.ae2utility.jeict_pattern_draft_invalid"));
            SequentialUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            return EncodeOutcome.FAILURE;
        }

        java.util.function.Predicate<appeng.api.stacks.AEKey> craftablePredicate =
                grid != null ? key -> grid.getCraftingService().isCraftable(key) : null;
        Set<AEKey> outputKeys = out.stream()
                .filter(java.util.Objects::nonNull)
                .map(GenericStack::what)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<GenericStack> in = new ArrayList<>();
        for (List<GenericStack> alts : inLists) {
            if (alts == null || alts.isEmpty()) {
                in.add(null);
            } else {
                GenericStack chosen = EncodePatternInputChooser.pickEncodedInput(alts, inventory, craftablePredicate,
                        payload.preserveInputOrder(), outputKeys::contains);
                if (chosen == null) {
                    serverPlayer.sendSystemMessage(Component.translatable("message.ae2utility.jeict_pattern_draft_invalid"));
                    SequentialUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
                    return EncodeOutcome.FAILURE;
                }
                in.add(chosen);
            }
        }

        if (in.isEmpty() || out.isEmpty()) {
            SequentialUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            return EncodeOutcome.FAILURE;
        }

        EncodeComputation computation = computeEncodedPattern(serverPlayer, payload, in, out, inventory, craftablePredicate);
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

        BlankPatternSource blankSource = consumeOneBlankPattern(serverPlayer, inventory, actionSource, blankPatternKey);
        if (blankSource == null) {
            if (!batchMode) {
                serverPlayer.sendSystemMessage(Component.translatable("message.ae2utility.encode_failed_no_blank_named",
                        payload.patternName().isBlank() ? "-" : payload.patternName()));
                SequentialUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            }
            return batchMode ? EncodeOutcome.BATCH_ABORT_NO_BLANK : EncodeOutcome.FAILURE;
        }

        try {
            rememberProviderSearchKey(serverPlayer, payload, encodedPattern);

            // ECO 优先：合成/锻造/切石样板（canUploadToMatrix）若网络存在 ECO 合成子系统，先尝试塞入 ECO；
            // ECO 满或无该子系统时返回 false，继续走下方原有上传逻辑（EAEP 装配矩阵 / 供应器）。
            if (payload.shiftDown() && canUploadToMatrix
                    && com.lhy.ae2utility.integration.eco.EcoReflection.isLoaded()
                    && com.lhy.ae2utility.integration.eco.EcoReflection.tryInsertPattern(grid, encodedPattern)) {
                String okName = payload.patternName().isBlank() ? sequentialResultLabel(payload) : payload.patternName();
                SequentialUploadResultBridge.sendImmediateResult(serverPlayer, okName, true);
                sendCraftableCacheRefreshIfNonEmpty(serverPlayer, payload);
                return EncodeOutcome.SUCCESS;
            }

            if (payload.shiftDown() && ModList.get().isLoaded("extendedae_plus")) {
                try {
                    IGrid eaepGrid = com.lhy.ae2utility.integration.eaep.EaepReflection.findPlayerGrid(serverPlayer);
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
                                disarmEaepShiftBlankRefund(serverPlayer);
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
                                disarmEaepShiftBlankRefund(serverPlayer);
                                giveEncodedPatternNoUpload(serverPlayer, encodedPattern);
                                serverPlayer.sendSystemMessage(
                                        Component.translatable("message.ae2utility.shift_batch_aborted_matrix_reject").withStyle(
                                                net.minecraft.ChatFormatting.GOLD));
                                SequentialUploadResultBridge.sendImmediateResult(serverPlayer, sequentialResultLabel(payload), false, true);
                                sendCraftableCacheRefreshIfNonEmpty(serverPlayer, payload);
                                return EncodeOutcome.FAILURE;
                            }
                        }

                        sendEaepProviderSearchSync(serverPlayer, payload);
                        SequentialUploadContextBridge.rememberGrid(serverPlayer, eaepGrid);
                        SequentialUploadContextBridge.rememberPendingSearchKey(serverPlayer,
                                deriveRawEaepSearchKeyForSync(serverPlayer, payload));
                        SequentialUploadContextBridge.rememberPendingProviderDisplayName(serverPlayer, payload.providerDisplayName());
                        com.lhy.ae2utility.integration.eaep.EaepReflection.clearPendingCtrlQUpload(serverPlayer);
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
                            armEaepShiftBlankForPendingProvider(serverPlayer, ctx, blankSource, blankPatternKey);
                            SequentialUploadResultBridge.sendAwaitingProviderUpload(serverPlayer, sequentialResultLabel(payload));
                        }
                        EaepUploadDebugLog.info(
                                "EncodePattern EAEP beginPendingCtrlQUpload sequential={} recipeId={} patternItem={} rememberedGrid=true",
                                payload.jeiSequentialQueue(), payload.recipeId(), encodedPattern.getItem());
                        return EncodeOutcome.EAEP_PROVIDER_UI_OPENED;
                    }
                } catch (Throwable e) {
                    SequentialUploadResultBridge.clearPendingName(serverPlayer);
                    boolean refundedShiftBlank = refundEaepShiftBlankIfPending(serverPlayer);
                    EaepUploadDebugLog.error("EncodePattern EAEP branch threw patternName=" + payload.patternName(), e);
                    String failName = payload.jeiSequentialQueue() ? sequentialResultLabel(payload) : payload.patternName();
                    SequentialUploadResultBridge.sendImmediateResult(serverPlayer, failName, false);
                    if (!refundedShiftBlank) {
                        refundBlankPattern(serverPlayer, inventory, actionSource, blankPatternKey, blankSource);
                    }
                    return EncodeOutcome.FAILURE;
                }
            }
            giveEncodedPatternNoUpload(serverPlayer, encodedPattern);
            disarmEaepShiftBlankRefund(serverPlayer);
            SequentialUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            sendCraftableCacheRefreshIfNonEmpty(serverPlayer, payload);
            return EncodeOutcome.SUCCESS;
        } catch (Throwable e) {
            SequentialUploadResultBridge.clearPendingName(serverPlayer);
            Ae2UtilityMod.LOGGER.error("Error encoding pattern: ", e);
            SequentialUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
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

        String mapped = com.lhy.ae2utility.integration.eaep.EaepReflection.mapRecipeTypeToSearchKey(recipeHolder.value());
        if (mapped != null && !mapped.isBlank()) {
            return mapped;
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

    private static boolean isValidPatternPayload(List<List<GenericStack>> inputs, List<GenericStack> outputs) {
        if (inputs == null || outputs == null || inputs.size() > NetworkValidation.MAX_RECIPE_INPUT_SLOTS
                || outputs.isEmpty() || outputs.size() > 27 || outputs.getFirst() == null) {
            return false;
        }
        long totalStacks = outputs.size();
        boolean hasInput = false;
        for (List<GenericStack> alternatives : inputs) {
            if (alternatives == null) continue;
            if (alternatives.size() > NetworkValidation.MAX_STACKS_PER_SLOT) return false;
            totalStacks += alternatives.size();
            if (totalStacks > NetworkValidation.MAX_TOTAL_PATTERN_STACKS) return false;
            for (GenericStack stack : alternatives) {
                if (stack == null) continue;
                if (stack.what() == null || stack.amount() <= 0) return false;
                hasInput = true;
            }
        }
        if (!hasInput) return false;
        for (GenericStack stack : outputs) {
            if (stack != null && (stack.what() == null || stack.amount() <= 0)) return false;
        }
        return outputs.getFirst().what() != null && outputs.getFirst().amount() > 0;
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

    private static int countMeaningfulInputs(List<GenericStack> inputStacks) {
        int count = 0;
        for (GenericStack inputStack : inputStacks) {
            if (inputStack != null && inputStack.what() != null && inputStack.amount() > 0) {
                count++;
            }
        }
        return count;
    }

    public static void disarmEaepShiftBlankRefund(ServerPlayer player) {
        if (player != null) {
            EAEP_SHIFT_BLANK_PENDING.remove(player.getUUID());
        }
    }

    /**
     * 已进入 EAEP「等供应器」路径后记录空白样板来源，{@link SequentialUploadResultBridge#flushPendingResult} 成功时卸下、失败则退还。
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
