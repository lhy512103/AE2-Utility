package com.lhy.ae2utility.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;

/**
 * 与 {@link com.lhy.ae2utility.service.EncodePatternService} 中「从每条输入的多选一 GenericStack 里选定一项」的逻辑保持一致，
 * 供客户端 Shift 预览与任意共用选股场景调用。
 */
public final class EncodePatternInputChooser {
    private EncodePatternInputChooser() {
    }

    /**
     * @param inventory 可为 null（视作网络不可用）：行为与服务端在无匹配库存时退回 specificity 排序首项一致。
     */
    public static @Nullable GenericStack pickEncodedInput(List<GenericStack> alts, @Nullable MEStorage inventory, boolean preserveInputOrder) {
        if (alts == null || alts.isEmpty()) {
            return null;
        }
        if (alts.size() == 1) {
            return alts.get(0);
        }
        if (preserveInputOrder) {
            return alts.get(0);
        }
        List<GenericStack> sortedAlts = new ArrayList<>(alts);
        sortedAlts.sort(Comparator.comparingInt(PullIngredientOrdering::genericStackItemSpecificityRank).reversed());
        if (inventory != null) {
            for (GenericStack alt : sortedAlts) {
                if (alt != null && alt.what() != null) {
                    long stored = inventory.getAvailableStacks().get(alt.what());
                    if (stored > 0) {
                        return alt;
                    }
                }
            }
        }
        return sortedAlts.get(0);
    }
}
