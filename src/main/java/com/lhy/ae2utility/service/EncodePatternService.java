package com.lhy.ae2utility.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.stacks.AEKey;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;

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
import com.lhy.ae2utility.init.ModDataComponents;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.InvalidateCraftableCachePacket;
import com.lhy.ae2utility.network.RecipeTreeOpenProvidersPacket;
import com.lhy.ae2utility.util.PullIngredientOrdering;

public final class EncodePatternService {
    private enum BlankPatternSource {
        PATTERN_TERMINAL_SLOT,
        PLAYER_INVENTORY,
        ME_NETWORK
    }

    private EncodePatternService() {}

    public static void handle(Player player, EncodePatternPacket payload) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        MEStorage inventory = null;
        IActionSource actionSource = null;
        IGrid grid = null;

        // 1. Try to resolve from open menu first (Block Terminal or Wireless Terminal GUI)
        if (serverPlayer.containerMenu instanceof MEStorageMenu storageMenu) {
            ITerminalHost host = storageMenu.getHost();
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

        // 2. If not found in open menu, try to resolve from wireless terminal in inventory/curios
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
            return;
        }

        if (actionSource == null) {
            actionSource = IActionSource.ofPlayer(serverPlayer);
        }

        RecipeTreeUploadResultBridge.clearPendingName(serverPlayer);
        RecipeTreeUploadContextBridge.clear(serverPlayer);

        // 3. Consume 1 Blank Pattern（与样板编码终端逻辑一致：先终端空白槽，再背包，再网络）
        AEItemKey blankPatternKey = AEItemKey.of(AEItems.BLANK_PATTERN);
        BlankPatternSource blankSource = consumeOneBlankPattern(serverPlayer, inventory, actionSource, blankPatternKey);
        if (blankSource == null) {
            serverPlayer.sendSystemMessage(Component.translatable("message.ae2utility.encode_failed_no_blank_named",
                    payload.patternName().isBlank() ? "-" : payload.patternName()));
            RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            return;
        }

        // 4. Get the inputs and outputs lists
        List<List<GenericStack>> inLists = payload.inputs();
        List<GenericStack> out = payload.outputs();

        List<GenericStack> in = new java.util.ArrayList<>();
        for (List<GenericStack> alts : inLists) {
            if (alts == null || alts.isEmpty()) {
                in.add(null);
            } else if (alts.size() == 1) {
                // Bookmarked or single option
                in.add(alts.get(0));
            } else {
                // Multiple options, check ME network (prefer exact component matches in storage)
                List<GenericStack> sortedAlts = new ArrayList<>(alts);
                sortedAlts.sort(Comparator.comparingInt(PullIngredientOrdering::genericStackItemSpecificityRank).reversed());
                GenericStack chosen = null;
                for (GenericStack alt : sortedAlts) {
                    if (alt != null && alt.what() != null) {
                        long stored = inventory.getAvailableStacks().get(alt.what());
                        if (stored > 0) {
                            chosen = alt;
                            break; // Found in ME network!
                        }
                    }
                }
                if (chosen == null) {
                    chosen = sortedAlts.get(0); // Fallback to first flashing item
                }
                in.add(chosen);
            }
        }

        if (in.isEmpty() || out.isEmpty()) {
            refundBlankPattern(serverPlayer, inventory, actionSource, blankPatternKey, blankSource);
            return;
        }

        int meaningfulInputCount = countMeaningfulInputs(in);

        // 5. Encode pattern
        try {
            ItemStack encodedPattern = ItemStack.EMPTY;
            boolean canUploadToMatrix = false;

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
                        encodedPattern = PatternDetailsHelper.encodeCraftingPattern((RecipeHolder) recipeHolder, inArray, outStack, payload.substitute(), payload.substituteFluids());
                        canUploadToMatrix = true;
                    } else if (recipeHolder.value() instanceof SmithingRecipe) {
                        AEItemKey template = in.size() > 0 && in.get(0) != null && in.get(0).what() instanceof AEItemKey k ? k : null;
                        AEItemKey base = in.size() > 1 && in.get(1) != null && in.get(1).what() instanceof AEItemKey k ? k : null;
                        AEItemKey addition = in.size() > 2 && in.get(2) != null && in.get(2).what() instanceof AEItemKey k ? k : null;
                        AEItemKey outStack = out.size() > 0 && out.get(0) != null && out.get(0).what() instanceof AEItemKey k ? k : null;
                        if (base != null && addition != null && outStack != null) {
                            encodedPattern = PatternDetailsHelper.encodeSmithingTablePattern((RecipeHolder) recipeHolder, template, base, addition, outStack, payload.substitute());
                            canUploadToMatrix = true;
                        }
                    } else if (recipeHolder.value() instanceof StonecutterRecipe) {
                        AEItemKey inKey = in.isEmpty() || in.get(0) == null ? null : (in.get(0).what() instanceof AEItemKey k ? k : null);
                        AEItemKey outKey = out.isEmpty() || out.get(0) == null ? null : (out.get(0).what() instanceof AEItemKey k ? k : null);
                        if (inKey != null && outKey != null) {
                            encodedPattern = PatternDetailsHelper.encodeStonecuttingPattern((RecipeHolder) recipeHolder, inKey, outKey, payload.substitute());
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

            if (!encodedPattern.isEmpty()) {
                rememberProviderSearchKey(serverPlayer, payload, encodedPattern);

                if (payload.shiftDown() && net.neoforged.fml.ModList.get().isLoaded("extendedae_plus")) {
                    try {
                        Class<?> pendingUtil = Class.forName("com.extendedae_plus.util.uploadPattern.CtrlQPendingUploadUtil");
                        java.lang.reflect.Method findGrid = pendingUtil.getMethod("findPlayerGrid", ServerPlayer.class);
                        IGrid eaepGrid = (IGrid) findGrid.invoke(null, serverPlayer);
                        if (eaepGrid == null) {
                            eaepGrid = grid;
                        }

                        if (canUploadToMatrix && eaepGrid != null) {
                            Class<?> uploadUtil = Class.forName("com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil");
                            try {
                                java.lang.reflect.Method duplicateCheck = uploadUtil.getDeclaredMethod("matrixContainsPattern",
                                        IGrid.class, ItemStack.class);
                                duplicateCheck.setAccessible(true);
                                boolean duplicate = (Boolean) duplicateCheck.invoke(null, eaepGrid, encodedPattern);
                                if (duplicate) {
                                    serverPlayer.sendSystemMessage(Component.translatable("extendedae_plus.message.matrix.duplicate"));
                                    RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
                                    refundBlankPattern(serverPlayer, inventory, actionSource, blankPatternKey, blankSource);
                                    return;
                                }
                            } catch (NoSuchMethodException ignored) {
                                // 旧版本 EAEP 没有这个方法时，继续走原有上传逻辑。
                            }

                            java.lang.reflect.Method uploadToMatrix = uploadUtil.getMethod("uploadPatternToMatrix", ServerPlayer.class, ItemStack.class, IGrid.class);
                            boolean uploaded = (Boolean) uploadToMatrix.invoke(null, serverPlayer, encodedPattern, eaepGrid);

                            // 只要矩阵上传成功就结束；失败则继续走 EAEP 的待上传逻辑，
                            // 让它自己弹供应器选择界面，而不是直接回退到玩家背包。
                            if (uploaded) {
                                RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), true);
                                sendCraftableCacheRefreshIfNonEmpty(serverPlayer, payload);
                                return;
                            }
                        }

                        // Use EAEP's UI upload for processing or if no matrix was found
                        presetEaepProviderSearchKey(serverPlayer, payload);
                        RecipeTreeUploadContextBridge.rememberGrid(serverPlayer, eaepGrid);
                        java.lang.reflect.Method beginUpload = pendingUtil.getMethod("beginPendingCtrlQUpload", ServerPlayer.class, ItemStack.class);
                        RecipeTreeUploadResultBridge.rememberPendingName(serverPlayer, payload.patternName());
                        beginUpload.invoke(null, serverPlayer, encodedPattern);
                        PacketDistributor.sendToPlayer(serverPlayer, new RecipeTreeOpenProvidersPacket());
                        sendCraftableCacheRefreshIfNonEmpty(serverPlayer, payload);
                        return;
                    } catch (Throwable e) {
                        RecipeTreeUploadResultBridge.clearPendingName(serverPlayer);
                        Ae2UtilityMod.LOGGER.error("Failed to integrate with ExtendedAE_Plus: {}", e.getMessage());
                    }
                }
                ItemHandlerHelper.giveItemToPlayer(serverPlayer, encodedPattern);
                RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
                sendCraftableCacheRefreshIfNonEmpty(serverPlayer, payload);
            } else {
                RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
                refundBlankPattern(serverPlayer, inventory, actionSource, blankPatternKey, blankSource);
            }
        } catch (Throwable e) {
            RecipeTreeUploadResultBridge.clearPendingName(serverPlayer);
            Ae2UtilityMod.LOGGER.error("Error encoding pattern: ", e);
            RecipeTreeUploadResultBridge.sendImmediateResult(serverPlayer, payload.patternName(), false);
            refundBlankPattern(serverPlayer, inventory, actionSource, blankPatternKey, blankSource);
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

    private static void presetEaepProviderSearchKey(ServerPlayer serverPlayer, EncodePatternPacket payload) {
        try {
            Class<?> uploadUtil = Class.forName("com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil");
            String searchKey = resolveProviderSearchKey(serverPlayer, payload, uploadUtil);
            if (payload.recipeId() != null) {
                var recipeHolder = serverPlayer.getServer().getRecipeManager().byKey(payload.recipeId()).orElse(null);
                if (recipeHolder != null && recipeHolder.value() instanceof CraftingRecipe
                        && (searchKey == null || searchKey.isBlank())) {
                    java.lang.reflect.Method preset = uploadUtil.getMethod("presetCraftingProviderSearchKey");
                    preset.invoke(null);
                    return;
                }
            }
            if (searchKey != null && !searchKey.isBlank()) {
                java.lang.reflect.Method setName = uploadUtil.getMethod("setLastProcessingName", String.class);
                setName.invoke(null, searchKey);
            }
        } catch (Throwable ignored) {
        }
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
}
