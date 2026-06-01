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
 * 与 {@link com.lhy.ae2utility.service.EncodePatternService} 中「从每条输入的多选一 GenericStack 里选定一项」的逻辑保持一致，
 * 供客户端 Shift 预览与任意共用选股场景调用。
 *
 * <p>选股优先级与原版 AE2「样板编码终端」对齐（{@code EncodingHelper.ENTRY_COMPARATOR}）：
 * <strong>可合成（网络中已有样板）→ 未损坏 → 库存最多</strong>，再退回组件特异性 / 首项。
 * 书签优先已在客户端构建候选列表时收窄为单项，故此处不再处理书签。</p>
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
        if (alts == null || alts.isEmpty()) {
            return null;
        }
        if (alts.size() == 1) {
            return alts.get(0);
        }
        if (preserveInputOrder) {
            return alts.get(0);
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
        // 候选全为空键：退回「组件特异性优先」（变体/带 NBT 的优先于素物品），再取首项
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
