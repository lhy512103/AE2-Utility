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
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
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
            List<GenericStack> in, List<GenericStack> out, @Nullable MEStorage inventory,
            @Nullable java.util.function.Predicate<AEKey> craftable) {
        ItemStack encodedPattern = ItemStack.EMPTY;
        boolean canUploadToMatrix = false;
        // 一旦把配方识别为合成/锻造/切石这类「结构化」配方，就绝不能再静默回退成处理样板，
        // 否则会出现「合成样板被写成处理样板」（原版 AE 编码终端不会这样）。
        boolean recognizedStructured = false;

        int meaningfulInputCount = countMeaningfulInputs(in);

        if (payload.recipeId() != null) {
            var recipeHolder = serverPlayer.getServer().getRecipeManager().byKey(payload.recipeId()).orElse(null);
            if (recipeHolder != null) {
                if (recipeHolder.value() instanceof CraftingRecipe craftingRecipe && meaningfulInputCount <= 9
                        && craftingRecipe.canCraftInDimensions(3, 3)) {
                    recognizedStructured = true;
                    // 对齐原版 AE2 EncodingHelper#encodeCraftingRecipe：按配方自身的 3x3 原料逐格摆放，
                    // 不依赖 JEI 槽位布局，避免映射错位导致编码失败而回退处理样板。
                    ItemStack[] inArray = buildCraftingGridFromRecipe(craftingRecipe, in, inventory, craftable);

                    ItemStack outStack = out.isEmpty() || out.get(0) == null ? ItemStack.EMPTY : toItemStack(out.get(0));
                    if (outStack == null) {
                        outStack = ItemStack.EMPTY;
                    }
                    encodedPattern = PatternDetailsHelper.encodeCraftingPattern((RecipeHolder) recipeHolder, inArray, outStack,
                            payload.substitute(), payload.substituteFluids());
                    canUploadToMatrix = true;
                } else if (recipeHolder.value() instanceof SmithingRecipe) {
                    recognizedStructured = true;
                    encodedPattern = encodeSmithingPatternFlexible((RecipeHolder<?>) recipeHolder, payload, in, out);
                    canUploadToMatrix = !encodedPattern.isEmpty();
                } else if (recipeHolder.value() instanceof StonecutterRecipe) {
                    recognizedStructured = true;
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

        if (encodedPattern.isEmpty() && payload.craftingCategoryHint() && meaningfulInputCount <= 9) {
            CraftingFallbackMatch fallback = findCraftingRecipeFromJeiInputs(serverPlayer, in);
            if (fallback != null) {
                // 仅在真正反查到 3x3 合成配方时才视为结构化合成；否则保持 false，让其按处理样板编码，
                // 避免 Create 动力合成器这类「JEI 看似合成、实则放不进 3x3」的配方点击后无任何样板产出。
                recognizedStructured = true;
                ItemStack outStack = out.isEmpty() || out.get(0) == null ? ItemStack.EMPTY : toItemStack(out.get(0));
                if (outStack == null) {
                    outStack = ItemStack.EMPTY;
                }
                encodedPattern = PatternDetailsHelper.encodeCraftingPattern((RecipeHolder) fallback.recipeHolder(),
                        fallback.inputs(), outStack, payload.substitute(), payload.substituteFluids());
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
            }
        }

        return new EncodeComputation(encodedPattern, canUploadToMatrix);
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
            inArray[i] = toItemStack(in.get(i));
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
            ItemStack itemStack = toItemStack(stack);
            if (!itemStack.isEmpty()) {
                inArray[outIndex++] = itemStack;
            }
        }
        return inArray;
    }

    /**
     * 按配方自身的 3x3 原料网格构建合成样板输入，逐格选择最优物品，对齐原版 AE2
     * {@code EncodingHelper#encodeCraftingRecipe} 的行为（与 JEI 槽位布局解耦）：
     * <ol>
     *     <li>优先沿用已选输入里能匹配该原料的物品（保留逐槽「可合成优先」选股结果）；</li>
     *     <li>否则按原料候选项统一选股（可合成 &gt; 未损坏 &gt; 库存最多）；</li>
     *     <li>再否则退回该原料的首个候选物品。</li>
     * </ol>
     */
    private static ItemStack[] buildCraftingGridFromRecipe(CraftingRecipe recipe, List<GenericStack> chosenInputs,
            @Nullable MEStorage inventory, @Nullable java.util.function.Predicate<AEKey> craftable) {
        ItemStack[] inArray = new ItemStack[9];
        Arrays.fill(inArray, ItemStack.EMPTY);
        List<Ingredient> ingredients3x3 =
                appeng.util.CraftingRecipeUtil.ensure3by3CraftingMatrix(recipe);

        List<ItemStack> chosenPool = new ArrayList<>();
        for (GenericStack g : chosenInputs) {
            if (g != null && g.what() != null) {
                ItemStack s = toItemStack(g);
                if (!s.isEmpty()) {
                    chosenPool.add(s);
                }
            }
        }

        for (int slot = 0; slot < 9 && slot < ingredients3x3.size(); slot++) {
            Ingredient ingredient = ingredients3x3.get(slot);
            if (ingredient.isEmpty()) {
                continue;
            }
            ItemStack chosen = ItemStack.EMPTY;
            for (java.util.Iterator<ItemStack> it = chosenPool.iterator(); it.hasNext();) {
                ItemStack candidate = it.next();
                if (ingredient.test(candidate)) {
                    chosen = candidate;
                    it.remove();
                    break;
                }
            }
            if (chosen.isEmpty()) {
                chosen = pickBestForIngredient(ingredient, inventory, craftable);
            }
            inArray[slot] = chosen;
        }
        return inArray;
    }

    private static ItemStack pickBestForIngredient(Ingredient ingredient,
            @Nullable MEStorage inventory, @Nullable java.util.function.Predicate<AEKey> craftable) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) {
            return ItemStack.EMPTY;
        }
        List<GenericStack> candidates = new ArrayList<>(items.length);
        for (ItemStack s : items) {
            AEItemKey key = AEItemKey.of(s);
            if (key != null) {
                candidates.add(new GenericStack(key, 1));
            }
        }
        if (!candidates.isEmpty()) {
            GenericStack best = EncodePatternInputChooser.pickEncodedInput(candidates, inventory, craftable, false);
            if (best != null && best.what() instanceof AEItemKey k) {
                return k.toStack();
            }
        }
        return items[0];
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
            RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer,
                    payload.patternName().isBlank() ? sequentialResultLabel(payload) : payload.patternName(), false);
            return true;
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

        java.util.function.Predicate<appeng.api.stacks.AEKey> craftablePredicate =
                grid != null ? key -> grid.getCraftingService().isCraftable(key) : null;
        List<GenericStack> in = new ArrayList<>();
        for (List<GenericStack> alts : inLists) {
            if (alts == null || alts.isEmpty()) {
                in.add(null);
            } else {
                in.add(EncodePatternInputChooser.pickEncodedInput(alts, inventory, craftablePredicate,
                        payload.preserveInputOrder()));
            }
        }

        if (in.isEmpty() || out.isEmpty()) {
            RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            return EncodeOutcome.FAILURE;
        }

        EncodeComputation computation = computeEncodedPattern(serverPlayer, payload, in, out, inventory, craftablePredicate);
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

            // ECO 优先：合成/锻造/切石样板（canUploadToMatrix）若网络存在 ECO 合成子系统，先尝试塞入 ECO；
            // ECO 满或无该子系统时返回 false，继续走下方原有上传逻辑（EAEP 装配矩阵 / 供应器）。
            if (payload.shiftDown() && canUploadToMatrix
                    && com.lhy.ae2utility.integration.eco.EcoReflection.isLoaded()
                    && com.lhy.ae2utility.integration.eco.EcoReflection.tryInsertPattern(grid, encodedPattern)) {
                String okName = payload.patternName().isBlank() ? sequentialResultLabel(payload) : payload.patternName();
                RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, okName, true);
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
                                RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, sequentialResultLabel(payload), false,
                                        true);
                                return EncodeOutcome.FAILURE;
                            }
                    } else {
                        /*
                         * 与 ExtendedAE Plus 对齐：只有「合成/锻造/切石」类样板（canUploadToMatrix=true）才尝试装配矩阵；
                         * 处理样板（processing）不进装配矩阵，直接走供应器待定队列，避免误判为「矩阵已满」而上传失败。
                         */
                        if (canUploadToMatrix) {
                            boolean uploadedMatrix = com.lhy.ae2utility.integration.eaep.EaepReflection
                                    .uploadPatternToMatrix(serverPlayer, encodedPattern, eaepGrid);
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
                                RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, sequentialResultLabel(payload), false, true);
                                sendCraftableCacheRefreshIfNonEmpty(serverPlayer, payload);
                                return EncodeOutcome.FAILURE;
                            }
                        }

                        sendEaepProviderSearchSync(serverPlayer, payload);
                        RecipeTreeUploadContextBridge.rememberGrid(serverPlayer, eaepGrid);
                        RecipeTreeUploadContextBridge.rememberPendingSearchKey(serverPlayer,
                                deriveRawEaepSearchKeyForSync(serverPlayer, payload));
                        RecipeTreeUploadContextBridge.rememberPendingProviderDisplayName(serverPlayer, payload.providerDisplayName());
                        com.lhy.ae2utility.integration.eaep.EaepReflection.clearPendingCtrlQUpload(serverPlayer);
                        RecipeTreeUploadResultBridge.rememberPendingName(serverPlayer, sequentialResultLabel(payload));
                        List<AEKey> pendingCraftableRefresh = collectCraftableKeysForRefresh(payload);
                        if (!pendingCraftableRefresh.isEmpty()) {
                            RecipeTreeUploadResultBridge.rememberPendingCraftableRefresh(serverPlayer, pendingCraftableRefresh);
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
