package com.lhy.ae2utility.jei;

import java.util.concurrent.atomic.AtomicInteger;

/** 单次 JEI 批量操作共享的会话 id；0 保留给单条编码。 */
public final class BulkEncodeSessions {
    private static final AtomicInteger NEXT = new AtomicInteger(1);

    private BulkEncodeSessions() {
    }

    public static int next() {
        int id = NEXT.getAndIncrement();
        if (id <= 0) {
            NEXT.set(2);
            return 1;
        }
        return id;
    }
}
