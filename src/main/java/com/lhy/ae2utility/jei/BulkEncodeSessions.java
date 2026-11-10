package com.lhy.ae2utility.jei;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单次 JEI「批量编码」鼠标操作内各 {@link com.lhy.ae2utility.network.EncodePatternPacket} 共享的会话 id（非 0），供服务端限流与去重聊天。
 */
public final class BulkEncodeSessions {
    /** 从 1 递增，避免出现 0（表示「非批量会话内单条」）。 */
    private static final AtomicInteger NEXT = new AtomicInteger(1);

    private BulkEncodeSessions() {
    }

    public static int next() {
        int v = NEXT.getAndIncrement();
        if (v <= 0) {
            NEXT.set(1);
            return 1;
        }
        return v;
    }
}
