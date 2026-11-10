package com.lhy.ae2utility.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 与 {@link com.lhy.ae2utility.service.EncodePatternService} 中「从每条输入的多选一 GenericStack 里选定一项」
 * 以及合成配方 3x3 网格重排的逻辑保持一致。
 *
 * <p>处理样板：在 JEI 槽候选里按原版 AE2 {@code EncodingHelper.ENTRY_COMPARATOR} 选
 * <strong>可合成 → 未损坏 → 库存最多</strong>。
 * 合成样板：不从 JEI 轮换列表猜，而是按 {@code EncodingHelper#encodeCraftingRecipe} 从
 * <strong>网络中匹配该 Ingredient 的物品</strong>里用同一套优先级选，没有网络匹配时再退回玩家背包 / 原料首项。</p>
 */
public final class EncodePatternInputChooser {
    private EncodePatternInputChooser() {
    }

    /**
     * @param inventory 可为 null（视作网络不可用）。
     * @param craftable 判断某 {@link AEKey} 在网络中是否「已有样板/可合成」；可为 null（视作均不可合成）。
     */
    public static @Nullable GenericStack pickEncodedInput(List<GenericStack> alts, @Nullable MEStorage inventory,
            @Nullable Predicate<AEKey> craftable, boolean preserveInputOrder) {
        return pickEncodedInput(alts, inventory, craftable, preserveInputOrder, key -> false);
    }

    public static @Nullable GenericStack pickEncodedInput(List<GenericStack> alts, @Nullable MEStorage inventory,
            @Nullable Predicate<AEKey> craftable, boolean preserveInputOrder, Predicate<AEKey> excluded) {
        if (alts == null || alts.isEmpty()) {
            return null;
        }
        List<GenericStack> eligible = alts.stream()
                .filter(alt -> alt != null && alt.what() != null && !excluded.test(alt.what()))
                .toList();
        if (eligible.isEmpty()) {
            return null;
        }
        if (eligible.size() == 1) {
            return eligible.getFirst();
        }
        if (preserveInputOrder) {
            return eligible.getFirst();
        }
        /*
         * 对齐原版 AE2 样板编码（appeng.integration.modules.itemlists.EncodingHelper#ENTRY_COMPARATOR）：
         * 在候选里依次比较 可合成(craftable) > 未损坏(undamaged) > 网络库存数量，选取优先级最高者。
         * 「可合成」即网络中已存在该物品的样板，优先用其编码。
         */
        KeyCounter available = inventory != null ? inventory.getAvailableStacks() : null;
        GenericStack best = null;
        int bestCraftable = -1;
        int bestUndamaged = -1;
        long bestStored = -1L;
        for (GenericStack alt : eligible) {
            if (alt == null || alt.what() == null) {
                continue;
            }
            AEKey what = alt.what();
            int isCraftable = craftable != null && craftable.test(what) ? 1 : 0;
            int isUndamaged = isUndamaged(what) ? 1 : 0;
            long stored = available != null ? available.get(what) : 0L;
            if (best == null
                    || isCraftable > bestCraftable
                    || (isCraftable == bestCraftable && isUndamaged > bestUndamaged)
                    || (isCraftable == bestCraftable && isUndamaged == bestUndamaged && stored > bestStored)) {
                best = alt;
                bestCraftable = isCraftable;
                bestUndamaged = isUndamaged;
                bestStored = stored;
            }
        }
        if (best != null) {
            return best;
        }
        // 候选全为空键：退回「组件特异性优先」（变体/带 NBT 的优先于素物品），再取首项
        List<GenericStack> sortedAlts = new ArrayList<>(eligible);
        sortedAlts.sort(Comparator.comparingInt(PullIngredientOrdering::genericStackItemSpecificityRank).reversed());
        return sortedAlts.get(0);
    }

    /**
     * 按配方自身的 3x3 原料网格构建合成样板输入，对齐原版 AE2
     * {@code EncodingHelper#encodeCraftingRecipe}：逐格从<strong>网络库存里匹配该原料</strong>的物品选
     * 可合成 &gt; 未损坏 &gt; 库存最多，不沿用 JEI 轮换当前项。
     */
    public static ItemStack[] buildCraftingGridFromRecipe(CraftingRecipe recipe,
            @Nullable MEStorage inventory, @Nullable Predicate<AEKey> craftable,
            Set<AEKey> outputKeys, @Nullable Iterable<ItemStack> playerInventory) {
        ItemStack[] inArray = new ItemStack[9];
        Arrays.fill(inArray, ItemStack.EMPTY);
        if (recipe == null) {
            return inArray;
        }
        List<Ingredient> ingredients3x3 = appeng.util.CraftingRecipeUtil.ensure3by3CraftingMatrix(recipe);
        Set<AEKey> excludedOutputs = outputKeys == null ? Set.of() : outputKeys;
        KeyCounter available = inventory != null ? inventory.getAvailableStacks() : null;

        for (int slot = 0; slot < 9 && slot < ingredients3x3.size(); slot++) {
            Ingredient ingredient = ingredients3x3.get(slot);
            if (ingredient.isEmpty()) {
                continue;
            }
            inArray[slot] = pickBestMatchingIngredient(ingredient, available, craftable, excludedOutputs,
                    playerInventory);
        }
        return inArray;
    }

    /**
     * 把 3x3 编码网格对齐回 JEI 输入槽顺序，供悬停预览按槽钉住。JEI 轮换项与实际写入不同时（深色橡木 vs 橡木原木），
     * 回传网格里真正写入的物品。
     */
    public static List<GenericStack> alignCraftingGridToJeiSlots(List<GenericStack> jeiSlots, ItemStack[] grid) {
        if (jeiSlots == null || jeiSlots.isEmpty()) {
            return List.of();
        }
        ItemStack[] inArray = grid == null ? new ItemStack[0] : grid;
        List<GenericStack> aligned = new ArrayList<>(jeiSlots.size());
        boolean[] used = new boolean[inArray.length];
        for (GenericStack jei : jeiSlots) {
            if (jei == null || jei.what() == null) {
                aligned.add(null);
                continue;
            }
            GenericStack found = null;
            for (int i = 0; i < inArray.length; i++) {
                if (used[i] || inArray[i] == null || inArray[i].isEmpty()) {
                    continue;
                }
                AEItemKey key = AEItemKey.of(inArray[i]);
                if (key != null && key.equals(jei.what())) {
                    found = new GenericStack(key, Math.max(1, inArray[i].getCount()));
                    used[i] = true;
                    break;
                }
            }
            if (found == null) {
                for (int i = 0; i < inArray.length; i++) {
                    if (used[i] || inArray[i] == null || inArray[i].isEmpty()) {
                        continue;
                    }
                    AEItemKey key = AEItemKey.of(inArray[i]);
                    if (key != null) {
                        found = new GenericStack(key, Math.max(1, inArray[i].getCount()));
                        used[i] = true;
                        break;
                    }
                }
            }
            aligned.add(found);
        }
        return java.util.Collections.unmodifiableList(aligned);
    }

    private static ItemStack pickBestMatchingIngredient(Ingredient ingredient, @Nullable KeyCounter available,
            @Nullable Predicate<AEKey> craftable, Set<AEKey> outputKeys,
            @Nullable Iterable<ItemStack> playerInventory) {
        AEItemKey bestKey = null;
        int bestCraftable = -1;
        int bestUndamaged = -1;
        long bestStored = -1L;

        if (available != null) {
            for (var entry : available) {
                if (!(entry.getKey() instanceof AEItemKey itemKey) || outputKeys.contains(itemKey)) {
                    continue;
                }
                if (!itemKey.matches(ingredient)) {
                    continue;
                }
                long stored = entry.getLongValue();
                if (stored <= 0 && (craftable == null || !craftable.test(itemKey))) {
                    continue;
                }
                if (isBetterCandidate(itemKey, stored, craftable, bestKey, bestCraftable, bestUndamaged, bestStored)) {
                    bestKey = itemKey;
                    bestCraftable = craftable != null && craftable.test(itemKey) ? 1 : 0;
                    bestUndamaged = isUndamaged(itemKey) ? 1 : 0;
                    bestStored = stored;
                }
            }
        }

        ItemStack[] items = ingredient.getItems();
        if (craftable != null) {
            for (ItemStack stack : items) {
                AEItemKey key = AEItemKey.of(stack);
                if (key == null || outputKeys.contains(key) || !craftable.test(key)) {
                    continue;
                }
                long stored = available != null ? available.get(key) : 0L;
                if (isBetterCandidate(key, stored, craftable, bestKey, bestCraftable, bestUndamaged, bestStored)) {
                    bestKey = key;
                    bestCraftable = 1;
                    bestUndamaged = isUndamaged(key) ? 1 : 0;
                    bestStored = stored;
                }
            }
        }

        if (bestKey != null) {
            return bestKey.toStack();
        }

        if (playerInventory != null) {
            for (ItemStack stack : playerInventory) {
                if (stack == null || stack.isEmpty() || !ingredient.test(stack)) {
                    continue;
                }
                AEItemKey key = AEItemKey.of(stack);
                if (key == null || outputKeys.contains(key)) {
                    continue;
                }
                return key.toStack();
            }
        }

        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(stack);
            if (key != null && outputKeys.contains(key)) {
                continue;
            }
            return stack.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    private static boolean isBetterCandidate(AEItemKey key, long stored, @Nullable Predicate<AEKey> craftable,
            @Nullable AEItemKey bestKey, int bestCraftable, int bestUndamaged, long bestStored) {
        int isCraftable = craftable != null && craftable.test(key) ? 1 : 0;
        int isUndamaged = isUndamaged(key) ? 1 : 0;
        if (bestKey == null) {
            return true;
        }
        if (isCraftable != bestCraftable) {
            return isCraftable > bestCraftable;
        }
        if (isUndamaged != bestUndamaged) {
            return isUndamaged > bestUndamaged;
        }
        return stored > bestStored;
    }

    public static ItemStack toItemStack(@Nullable GenericStack stack) {
        if (stack == null) {
            return ItemStack.EMPTY;
        }
        if (stack.what() instanceof AEItemKey itemKey) {
            int count = (int) Math.max(1, Math.min(Integer.MAX_VALUE, stack.amount()));
            return itemKey.toStack(count);
        }
        ItemStack wrapped = GenericStack.wrapInItemStack(stack);
        return wrapped == null ? ItemStack.EMPTY : wrapped;
    }

    private static boolean isUndamaged(AEKey what) {
        if (what instanceof AEItemKey itemKey) {
            return !itemKey.isDamaged();
        }
        return true;
    }
}
