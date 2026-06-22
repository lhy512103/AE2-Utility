package com.lhy.ae2utility.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

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
        if (alts == null || alts.isEmpty()) {
            return null;
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

    private static boolean isUndamaged(AEKey what) {
        if (what instanceof AEItemKey itemKey) {
            return !itemKey.toStack().isDamaged();
        }
        return true;
    }
}
