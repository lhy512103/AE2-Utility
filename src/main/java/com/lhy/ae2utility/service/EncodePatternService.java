package com.lhy.ae2utility.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.ItemHandlerHelper;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEItems;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.SlotSemantics;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.compat.WcwtCompat;
import com.lhy.ae2utility.network.CraftableStatePacket;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.ModNetworking;
import com.lhy.ae2utility.network.OpenEaepProviderSelectionPacket;
import com.lhy.ae2utility.util.EncodePatternInputChooser;

/**
 * 1.20.1 forge：服务端样板编码 + EAEP 上传。
 *
 * <p>核心流程：
 * <ol>
 *   <li>解析编码上下文（从已打开的 ME 终端或背包无线终端获取 MEStorage/IGrid）</li>
 *   <li>从 JEI 多候选项中选股</li>
 *   <li>编码样板（合成/锻造/切石/处理）</li>
 *   <li>消耗空白样板</li>
 *   <li>Shift + EAEP 已安装：先尝试装配矩阵上传，再退回供应器待定队列</li>
 *   <li>非 Shift 或 EAEP 未安装：直接把样板给玩家</li>
 * </ol>
 * 直接 import EAEP 公开 API（compileOnly 依赖），不使用反射。</p>
 */
public final class EncodePatternService {

    private EncodePatternService() {
    }

    private record EncodeContext(MEStorage inventory, IActionSource actionSource, @Nullable IGrid grid) {
    }

    private record EncodeComputation(ItemStack encodedPattern, boolean canUploadToMatrix) {
    }

    private enum BlankPatternSource {
        PATTERN_TERMINAL_SLOT, PLAYER_INVENTORY, ME_NETWORK
    }

    public static void handle(Player player, EncodePatternPacket payload) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        encodePatternInternal(serverPlayer, payload);
    }

    // ── 解析编码上下文 ────────────────────────────────────────────
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
            if (resolution.isReady() && resolution.host() != null) {
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

    // ── 主编码逻辑 ────────────────────────────────────────────────
    private static void encodePatternInternal(ServerPlayer serverPlayer, EncodePatternPacket payload) {
        EncodeContext ctx = resolveEncodeContext(serverPlayer);
        if (ctx == null) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.ae2utility.encode_failed_no_terminal")
                            .withStyle(ChatFormatting.RED));
            return;
        }

        MEStorage inventory = ctx.inventory();
        IActionSource actionSource = ctx.actionSource();
        IGrid grid = ctx.grid();

        Predicate<AEKey> craftablePredicate =
                grid != null ? key -> grid.getCraftingService().isCraftable(key) : null;

        // 从多候选项中选股
        List<GenericStack> in = new ArrayList<>();
        for (List<GenericStack> alts : payload.inputs()) {
            if (alts == null || alts.isEmpty()) {
                in.add(null);
            } else {
                in.add(EncodePatternInputChooser.pickEncodedInput(alts, inventory, craftablePredicate,
                        payload.preserveInputOrder()));
            }
        }

        List<GenericStack> out = payload.outputs();
        if (in.isEmpty() || out.isEmpty()) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.ae2utility.encode_failed_empty"));
            return;
        }

        EncodeComputation computation = computeEncodedPattern(serverPlayer, payload, in, out, inventory,
                craftablePredicate);
        ItemStack encodedPattern = computation.encodedPattern();
        if (encodedPattern.isEmpty()) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.ae2utility.encode_failed",
                            payload.patternName().isBlank() ? "-" : payload.patternName()));
            return;
        }
        tagEaepEncodePlayer(serverPlayer, encodedPattern);

        // 消耗空白样板
        AEItemKey blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN);
        BlankPatternSource blankSource = consumeOneBlankPattern(serverPlayer, inventory, actionSource,
                blankPatternKey);
        if (blankSource == null) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.ae2utility.encode_failed_no_blank_named",
                            payload.patternName().isBlank() ? "-" : payload.patternName()));
            return;
        }

        try {
            // Shift + EAEP 上传
            if (payload.shiftDown() && ModList.get().isLoaded("extendedae_plus")) {
                if (tryEaepUpload(serverPlayer, payload, encodedPattern, computation.canUploadToMatrix(),
                        grid, ctx, blankSource, blankPatternKey)) {
                    return;
                }
            }

            // 直接给玩家
            giveEncodedPatternToPlayer(serverPlayer, encodedPattern);
        } catch (Throwable e) {
            Ae2UtilityMod.LOGGER.error("Error encoding pattern: ", e);
            refundBlankPattern(serverPlayer, inventory, actionSource, blankPatternKey, blankSource);
        }
    }

    // ── 编码样板（合成/锻造/切石/处理）────────────────────────────
    private static EncodeComputation computeEncodedPattern(ServerPlayer serverPlayer, EncodePatternPacket payload,
            List<GenericStack> in, List<GenericStack> out, @Nullable MEStorage inventory,
            @Nullable Predicate<AEKey> craftable) {
        ItemStack encodedPattern = ItemStack.EMPTY;
        boolean canUploadToMatrix = false;
        boolean recognizedStructured = false;

        int meaningfulInputCount = countMeaningfulInputs(in);

        if (payload.recipeId() != null) {
            Recipe<?> recipe = serverPlayer.getServer().getRecipeManager()
                    .byKey(payload.recipeId()).orElse(null);
            if (recipe != null) {
                if (recipe instanceof CraftingRecipe craftingRecipe
                        && meaningfulInputCount <= 9
                        && craftingRecipe.canCraftInDimensions(3, 3)) {
                    recognizedStructured = true;
                    ItemStack[] inArray = buildCraftingGridFromRecipe(craftingRecipe, in, inventory, craftable);
                    ItemStack outStack = toItemStack(out.isEmpty() ? null : out.get(0));
                    encodedPattern = PatternDetailsHelper.encodeCraftingPattern(craftingRecipe, inArray, outStack,
                            payload.substitute(), payload.substituteFluids());
                    canUploadToMatrix = true;
                } else if (recipe instanceof SmithingRecipe smithingRecipe) {
                    recognizedStructured = true;
                    encodedPattern = encodeSmithingPatternFlexible(smithingRecipe, payload, in, out);
                    canUploadToMatrix = !encodedPattern.isEmpty();
                } else if (recipe instanceof StonecutterRecipe stonecutterRecipe) {
                    recognizedStructured = true;
                    AEItemKey inKey = firstItemKey(in);
                    AEItemKey outKey = firstItemKey(out);
                    if (inKey != null && outKey != null) {
                        encodedPattern = PatternDetailsHelper.encodeStonecuttingPattern(stonecutterRecipe, inKey, outKey,
                                payload.substitute());
                        canUploadToMatrix = true;
                    }
                }
            }
        }

        // 只有 JEI 合成分类才用输入反查 3x3 合成；其它机器/处理类配方直接走处理样板。
        if (encodedPattern.isEmpty() && !recognizedStructured && payload.craftingCategoryHint()
                && meaningfulInputCount <= 9) {
            CraftingFallbackMatch fallback = findCraftingRecipeFromJeiInputs(serverPlayer, in);
            if (fallback != null) {
                recognizedStructured = true;
                ItemStack outStack = toItemStack(out.isEmpty() ? null : out.get(0));
                encodedPattern = PatternDetailsHelper.encodeCraftingPattern(fallback.recipe(), fallback.inputs(),
                        outStack, payload.substitute(), payload.substituteFluids());
                canUploadToMatrix = true;
            }
        }

        // 结构化配方编码失败不回退处理样板
        if (encodedPattern.isEmpty() && !recognizedStructured) {
            List<GenericStack> procIn = in.stream().filter(java.util.Objects::nonNull).toList();
            List<GenericStack> procOut = out.stream().filter(java.util.Objects::nonNull).toList();
            if (!procIn.isEmpty() && !procOut.isEmpty()) {
                encodedPattern = PatternDetailsHelper.encodeProcessingPattern(
                        procIn.toArray(new GenericStack[0]), procOut.toArray(new GenericStack[0]));
            }
        }

        return new EncodeComputation(encodedPattern, canUploadToMatrix);
    }

    // ── 锻造样板编码 ──────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private static ItemStack encodeSmithingPatternFlexible(SmithingRecipe recipe, EncodePatternPacket payload,
            List<GenericStack> in, List<GenericStack> out) {
        AEItemKey outKey = firstItemKey(out);
        if (outKey == null) {
            return ItemStack.EMPTY;
        }
        AEItemKey template = positionalItemKey(in, 0);
        AEItemKey base = positionalItemKey(in, 1);
        AEItemKey addition = positionalItemKey(in, 2);

        ItemStack stacked = tryEncodeSmithing(recipe, payload.substitute(), template, base, addition, outKey);
        if (!stacked.isEmpty()) {
            return stacked;
        }

        // JEI 锻造台槽位可能少于 3，按有效物品顺序再试
        List<AEItemKey> keys = flattenItemKeys(in);
        if (keys.size() >= 3) {
            stacked = tryEncodeSmithing(recipe, payload.substitute(), keys.get(0), keys.get(1), keys.get(2), outKey);
        } else if (keys.size() == 2) {
            stacked = tryEncodeSmithing(recipe, payload.substitute(), null, keys.get(0), keys.get(1), outKey);
        }
        return stacked;
    }

    private static ItemStack tryEncodeSmithing(SmithingRecipe recipe, boolean substitute,
            @Nullable AEItemKey template, @Nullable AEItemKey base, @Nullable AEItemKey addition,
            AEItemKey outKey) {
        if (base == null || addition == null || outKey == null) {
            return ItemStack.EMPTY;
        }
        try {
            return PatternDetailsHelper.encodeSmithingTablePattern(recipe, template, base, addition, outKey,
                    substitute);
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    // ── 合成配方反查 ──────────────────────────────────────────────
    private record CraftingFallbackMatch(CraftingRecipe recipe, ItemStack[] inputs) {
    }

    @SuppressWarnings("unchecked")
    private static @Nullable CraftingFallbackMatch findCraftingRecipeFromJeiInputs(ServerPlayer serverPlayer,
            List<GenericStack> in) {
        ItemStack[] sparseInputs = buildSparseCraftingInputs(in);
        TransientCraftingContainer sparseInput = new TransientCraftingContainer(null, 3, 3);
        for (int i = 0; i < sparseInputs.length; i++) {
            sparseInput.setItem(i, sparseInputs[i]);
        }
        var sparseMatch = serverPlayer.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, sparseInput, serverPlayer.level());
        if (sparseMatch.isPresent()) {
            return new CraftingFallbackMatch((CraftingRecipe) sparseMatch.get(), sparseInputs);
        }

        ItemStack[] compactInputs = buildCompactCraftingInputs(in);
        if (Arrays.equals(sparseInputs, compactInputs)) {
            return null;
        }
        TransientCraftingContainer compactInput = new TransientCraftingContainer(null, 3, 3);
        for (int i = 0; i < compactInputs.length; i++) {
            compactInput.setItem(i, compactInputs[i]);
        }
        var compactMatch = serverPlayer.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, compactInput, serverPlayer.level());
        return compactMatch
                .map(r -> new CraftingFallbackMatch((CraftingRecipe) r, compactInputs))
                .orElse(null);
    }

    private static ItemStack[] buildSparseCraftingInputs(List<GenericStack> in) {
        ItemStack[] inArray = new ItemStack[9];
        Arrays.fill(inArray, ItemStack.EMPTY);
        for (int i = 0; i < Math.min(9, in.size()); i++) {
            inArray[i] = toItemStack(in.get(i));
        }
        return inArray;
    }

    private static ItemStack[] buildCompactCraftingInputs(List<GenericStack> in) {
        ItemStack[] inArray = new ItemStack[9];
        Arrays.fill(inArray, ItemStack.EMPTY);
        int outIndex = 0;
        for (GenericStack stack : in) {
            if (outIndex >= 9) break;
            ItemStack itemStack = toItemStack(stack);
            if (!itemStack.isEmpty()) {
                inArray[outIndex++] = itemStack;
            }
        }
        return inArray;
    }

    // ── 按配方 3x3 原料网格构建合成输入 ──────────────────────────
    private static ItemStack[] buildCraftingGridFromRecipe(CraftingRecipe recipe, List<GenericStack> chosenInputs,
            @Nullable MEStorage inventory, @Nullable Predicate<AEKey> craftable) {
        ItemStack[] inArray = new ItemStack[9];
        Arrays.fill(inArray, ItemStack.EMPTY);
        List<net.minecraft.world.item.crafting.Ingredient> ingredients3x3 =
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
            net.minecraft.world.item.crafting.Ingredient ingredient = ingredients3x3.get(slot);
            if (ingredient.isEmpty()) continue;
            ItemStack chosen = ItemStack.EMPTY;
            for (var it = chosenPool.iterator(); it.hasNext();) {
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

    private static ItemStack pickBestForIngredient(net.minecraft.world.item.crafting.Ingredient ingredient,
            @Nullable MEStorage inventory, @Nullable Predicate<AEKey> craftable) {
        ItemStack[] items = ingredient.getItems();
        if (items.length == 0) return ItemStack.EMPTY;
        List<GenericStack> candidates = new ArrayList<>(items.length);
        for (ItemStack s : items) {
            AEItemKey key = AEItemKey.of(s);
            if (key != null) candidates.add(new GenericStack(key, 1));
        }
        if (!candidates.isEmpty()) {
            GenericStack best = EncodePatternInputChooser.pickEncodedInput(candidates, inventory, craftable, false);
            if (best != null && best.what() instanceof AEItemKey k) {
                return k.toStack();
            }
        }
        return items[0];
    }

    // ── EAEP 上传 ────────────────────────────────────────────────
    /**
     * 直接调用 EAEP 公开 API（compileOnly 依赖），不使用反射。
     * @return true 表示已处理（上传成功或已进入供应器待定队列），false 表示未处理需走默认逻辑
     */
    private static boolean tryEaepUpload(ServerPlayer serverPlayer, EncodePatternPacket payload,
            ItemStack encodedPattern, boolean canUploadToMatrix, @Nullable IGrid grid,
            EncodeContext ctx, BlankPatternSource blankSource, AEItemKey blankPatternKey) {
        IGrid eaepGrid = grid;
        if (eaepGrid == null) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("message.ae2utility.encode_failed_no_network")
                            .withStyle(ChatFormatting.RED));
            refundBlankPattern(serverPlayer, ctx.inventory(), ctx.actionSource(), blankPatternKey, blankSource);
            return true;
        }

        // 1. 可进矩阵的样板（合成/锻造/切石）先尝试装配矩阵
        if (canUploadToMatrix) {
            try {
                boolean uploaded = com.extendedae_plus.util.uploadPattern.MatrixUploadUtil
                        .uploadPatternToMatrix(serverPlayer, encodedPattern, eaepGrid);
                if (uploaded) {
                    sendCraftableCacheRefresh(serverPlayer, payload);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        // 2. 矩阵上传失败或不适用时，走供应器待定队列（打开 EAEP 供应器选择界面）
        try {
            ItemStack pendingPattern = encodedPattern.copy();

            var result = com.extendedae_plus.util.uploadPattern.ProviderUploadUtil
                    .beginPendingCtrlQUpload(serverPlayer, pendingPattern);
            if (result == null) {
                PendingCraftableRefreshService.clear(serverPlayer);
                // 供应器待定队列也失败，把样板给玩家
                serverPlayer.sendSystemMessage(
                        Component.translatable("message.ae2utility.encode_eaep_pending_failed")
                                .withStyle(ChatFormatting.YELLOW));
                giveEncodedPatternToPlayer(serverPlayer, encodedPattern);
                return true;
            }

            // 设置供应器搜索关键字
            String searchKey = payload.providerSearchKey();
            if (searchKey != null && !searchKey.isBlank()) {
                com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig.setLastProviderSearchKey(searchKey);
            } else if (shouldPresetCraftingProviderSearch(serverPlayer, payload, canUploadToMatrix)) {
                com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig.presetCraftingProviderSearchKey();
            }

            PendingCraftableRefreshService.remember(serverPlayer, collectOutputKeysForRefresh(payload));
            sendEaepProviderSelectionRequest(serverPlayer, payload, canUploadToMatrix);
            return true;
        } catch (Throwable e) {
            Ae2UtilityMod.LOGGER.error("EAEP upload failed", e);
            PendingCraftableRefreshService.clear(serverPlayer);
            refundBlankPattern(serverPlayer, ctx.inventory(), ctx.actionSource(), blankPatternKey, blankSource);
            return true;
        }
    }

    private static void tagEaepEncodePlayer(ServerPlayer serverPlayer, ItemStack encodedPattern) {
        if (encodedPattern.isEmpty() || !ModList.get().isLoaded("extendedae_plus")) {
            return;
        }
        encodedPattern.getOrCreateTag().putString("encodePlayer", serverPlayer.getGameProfile().getName());
    }

    private static void sendEaepProviderSelectionRequest(ServerPlayer serverPlayer, EncodePatternPacket payload,
            boolean canUploadToMatrix) {
        boolean craftingPresetOnly = shouldPresetCraftingProviderSearch(serverPlayer, payload, canUploadToMatrix);
        String rawFilter = craftingPresetOnly ? "" : deriveRawEaepSearchKeyForSync(serverPlayer, payload);
        ModNetworking.sendToPlayer(serverPlayer, new OpenEaepProviderSelectionPacket(craftingPresetOnly, rawFilter));
    }

    private static boolean shouldPresetCraftingProviderSearch(ServerPlayer serverPlayer, EncodePatternPacket payload,
            boolean canUploadToMatrix) {
        String searchKey = payload.providerSearchKey();
        if (searchKey != null && !searchKey.isBlank()) {
            return "crafting".equalsIgnoreCase(searchKey.trim()) && isCraftingRecipePayload(serverPlayer, payload);
        }
        return isCraftingRecipePayload(serverPlayer, payload)
                || (payload.recipeId() == null && canUploadToMatrix);
    }

    private static boolean isCraftingRecipePayload(ServerPlayer serverPlayer, EncodePatternPacket payload) {
        if (payload.recipeId() == null || serverPlayer.getServer() == null) {
            return false;
        }
        return serverPlayer.getServer().getRecipeManager().byKey(payload.recipeId())
                .map(recipe -> recipe instanceof CraftingRecipe)
                .orElse(false);
    }

    private static String deriveRawEaepSearchKeyForSync(ServerPlayer serverPlayer, EncodePatternPacket payload) {
        String searchKey = payload.providerSearchKey();
        if (searchKey != null && !searchKey.isBlank()) {
            return searchKey;
        }
        if (payload.recipeId() == null || serverPlayer.getServer() == null) {
            return "";
        }

        Recipe<?> recipe = serverPlayer.getServer().getRecipeManager().byKey(payload.recipeId()).orElse(null);
        if (recipe == null) {
            return "";
        }
        if (recipe instanceof CraftingRecipe) {
            return "crafting";
        }

        try {
            String mapped = com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig.mapRecipeTypeToSearchKey(recipe);
            if (mapped != null && !mapped.isBlank()) {
                return mapped;
            }
        } catch (Throwable ignored) {
        }

        ResourceLocation recipeTypeId = net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        return recipeTypeId != null ? recipeTypeId.getPath() : "";
    }

    // ── 空白样板消耗 ──────────────────────────────────────────────
    private static @Nullable BlankPatternSource consumeOneBlankPattern(ServerPlayer serverPlayer,
            MEStorage inventory, IActionSource actionSource, AEItemKey blankPatternKey) {
        // 1. 已打开样板编码终端：从空白样板槽消耗
        if (serverPlayer.containerMenu instanceof PatternEncodingTermMenu patternMenu) {
            for (Slot slot : patternMenu.getSlots(SlotSemantics.BLANK_PATTERN)) {
                ItemStack stack = slot.getItem();
                if (isBlankPattern(stack)) {
                    stack.shrink(1);
                    slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
                    slot.setChanged();
                    patternMenu.broadcastChanges();
                    return BlankPatternSource.PATTERN_TERMINAL_SLOT;
                }
            }
        } else if (WcwtCompat.isWcwtMenu(serverPlayer.containerMenu)) {
            for (Slot slot : serverPlayer.containerMenu.slots) {
                ItemStack stack = slot.getItem();
                if (isBlankPattern(stack)) {
                    stack.shrink(1);
                    slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
                    slot.setChanged();
                    serverPlayer.containerMenu.broadcastChanges();
                    return BlankPatternSource.PATTERN_TERMINAL_SLOT;
                }
            }
        }

        // 2. 玩家背包
        for (int i = 0; i < serverPlayer.getInventory().getContainerSize(); i++) {
            ItemStack stack = serverPlayer.getInventory().getItem(i);
            if (isBlankPattern(stack)) {
                stack.shrink(1);
                serverPlayer.getInventory().setChanged();
                return BlankPatternSource.PLAYER_INVENTORY;
            }
        }

        // 3. ME 网络
        long extracted = inventory.extract(blankPatternKey, 1, Actionable.MODULATE, actionSource);
        if (extracted > 0) {
            return BlankPatternSource.ME_NETWORK;
        }
        return null;
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
                        if (isBlankPattern(cur) && cur.getCount() < cur.getMaxStackSize()) {
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
                        if (isBlankPattern(cur) && cur.getCount() < cur.getMaxStackSize()) {
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

    // ── 工具方法 ──────────────────────────────────────────────────
    private static boolean isBlankPattern(ItemStack stack) {
        return !stack.isEmpty() && stack.is(AEItems.BLANK_PATTERN.asItem());
    }

    private static void giveEncodedPatternToPlayer(ServerPlayer player, ItemStack encodedPattern) {
        ItemStack stack = encodedPattern.copy();
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    private static void sendCraftableCacheRefresh(ServerPlayer player, EncodePatternPacket payload) {
        List<AEKey> keys = collectOutputKeysForRefresh(payload);
        if (!keys.isEmpty()) {
            ModNetworking.sendToPlayer(player, new CraftableStatePacket(keys, List.of()));
        }
    }

    private static List<AEKey> collectOutputKeysForRefresh(EncodePatternPacket payload) {
        Set<AEKey> keys = new LinkedHashSet<>();
        for (GenericStack output : payload.outputs()) {
            if (output != null && output.what() != null) {
                keys.add(output.what());
            }
        }
        return List.copyOf(keys);
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

    private static @Nullable AEItemKey positionalItemKey(List<GenericStack> in, int index) {
        if (index < 0 || index >= in.size()) return null;
        GenericStack g = in.get(index);
        return g != null && g.what() instanceof AEItemKey k ? k : null;
    }

    private static @Nullable AEItemKey firstItemKey(List<GenericStack> stacks) {
        for (GenericStack g : stacks) {
            if (g != null && g.what() instanceof AEItemKey k) return k;
        }
        return null;
    }

    private static List<AEItemKey> flattenItemKeys(List<GenericStack> in) {
        List<AEItemKey> keys = new ArrayList<>(4);
        for (GenericStack g : in) {
            if (g != null && g.what() instanceof AEItemKey k) {
                keys.add(k);
            }
        }
        return keys;
    }

    private static ItemStack toItemStack(@Nullable GenericStack stack) {
        if (stack == null) return ItemStack.EMPTY;
        if (stack.what() instanceof AEItemKey itemKey) {
            int count = (int) Math.max(1, Math.min(Integer.MAX_VALUE, stack.amount()));
            return itemKey.toStack(count);
        }
        ItemStack wrapped = GenericStack.wrapInItemStack(stack);
        return wrapped == null ? ItemStack.EMPTY : wrapped;
    }
}
