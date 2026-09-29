package com.lhy.ae2utility.util;

import java.util.ArrayList;
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

/**
 * 与 {@link com.lhy.ae2utility.service.EncodePatternService} 中「从每条输入的多选一 GenericStack 里选定一项」一致，
 * 供客户端预览与服务器共用选股数据源。
 *
 * <p>选股优先级对齐原版 AE2 样板编码：可合成（已有样板）→ 未损坏 → 库存最多，再退回首项。</p>
 */
public final class EncodePatternInputChooser {
    private EncodePatternInputChooser() {
    }

    public static @Nullable GenericStack pickEncodedInput(List<GenericStack> alts, @Nullable MEStorage inventory,
            @Nullable Predicate<AEKey> craftable, boolean preserveInputOrder) {
        return pickEncodedInput(alts, inventory, craftable, preserveInputOrder, Set.of());
    }

    /**
     * {@code excluded}（通常是配方产物）只在还有其它候选时才剔除，避免生成自循环样板又不丢输入。
     */
    public static @Nullable GenericStack pickEncodedInput(List<GenericStack> alts, @Nullable MEStorage inventory,
            @Nullable Predicate<AEKey> craftable, boolean preserveInputOrder, Set<AEKey> excluded) {
        if (alts == null || alts.isEmpty()) {
            return null;
        }
        if (!excluded.isEmpty()) {
            List<GenericStack> eligible = alts.stream()
                    .filter(alt -> alt != null && alt.what() != null && !excluded.contains(alt.what()))
                    .toList();
            if (!eligible.isEmpty()) {
                alts = eligible;
            }
        }
        if (alts.size() == 1) {
            return alts.get(0);
        }
        if (preserveInputOrder) {
            return alts.get(0);
        }

        KeyCounter available = inventory != null ? inventory.getAvailableStacks() : null;
        GenericStack best = null;
        int bestCraftable = -1;
        int bestUndamaged = -1;
        long bestStored = -1L;
        for (GenericStack alt : alts) {
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
        List<GenericStack> sortedAlts = new ArrayList<>(alts);
        sortedAlts.sort(Comparator.comparingInt(PullIngredientOrdering::genericStackItemSpecificityRank).reversed());
        return sortedAlts.get(0);
    }

    /**
     * 把 3x3 编码网格对齐回 JEI 输入槽顺序，供悬停预览按槽钉住实际写入的物品。
     */
    public static List<GenericStack> alignCraftingGridToJeiSlots(List<GenericStack> jeiSlots, ItemStack[] grid) {
        List<GenericStack> aligned = new ArrayList<>(jeiSlots.size());
        boolean[] used = new boolean[grid.length];
        for (GenericStack jei : jeiSlots) {
            if (jei == null || jei.what() == null) {
                aligned.add(null);
                continue;
            }
            int match = -1;
            for (int pass = 0; pass < 2 && match < 0; pass++) {
                for (int i = 0; i < grid.length; i++) {
                    if (used[i] || grid[i] == null || grid[i].isEmpty()) {
                        continue;
                    }
                    AEItemKey key = AEItemKey.of(grid[i]);
                    if (key != null && (pass == 1 || key.equals(jei.what()))) {
                        match = i;
                        break;
                    }
                }
            }
            if (match < 0) {
                aligned.add(null);
            } else {
                used[match] = true;
                aligned.add(new GenericStack(AEItemKey.of(grid[match]), Math.max(1, grid[match].getCount())));
            }
        }
        return java.util.Collections.unmodifiableList(aligned);
    }

    private static boolean isUndamaged(AEKey what) {
        if (what instanceof AEItemKey itemKey) {
            return !itemKey.toStack().isDamaged();
        }
        return true;
    }
}
