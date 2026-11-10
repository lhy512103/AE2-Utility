package com.lhy.ae2utility.service;

import java.util.List;

import com.lhy.ae2utility.network.EncodePatternPacket;

/** Pure batch policy extracted from the server-side encoding workflow. */
public final class EncodeBatchPolicy {
    private EncodeBatchPolicy() {
    }

    public static EncodePatternPacket firstFullCategoryBatch(List<EncodePatternPacket> patterns) {
        return patterns.stream().filter(EncodePatternPacket::jeiFullCategoryBatch).findFirst().orElse(null);
    }

    public static List<EncodePatternPacket> cap(List<EncodePatternPacket> patterns, int maxPatterns) {
        if (maxPatterns <= 0 || patterns.size() <= maxPatterns) {
            return patterns;
        }
        return List.copyOf(patterns.subList(0, maxPatterns));
    }
}
