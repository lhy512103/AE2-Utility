package com.lhy.ae2utility.client;

import java.util.concurrent.atomic.AtomicInteger;

/** One wire request-ID namespace for all recipe viewers listening to preview responses. */
public final class PreviewRequestIds {
    private static final AtomicInteger NEXT = new AtomicInteger(1);

    private PreviewRequestIds() {}

    public static int next() {
        int id;
        do {
            id = NEXT.getAndIncrement();
        } while (id == 0); // Zero remains the local sentinel for an invalidated request.
        return id;
    }
}
