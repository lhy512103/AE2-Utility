package com.lhy.ae2utility.card;

/** Operation-local, nestable restoration. Always close on the entering thread. */
public final class NbtTearScope implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private Runnable restore;

    private NbtTearScope(Runnable restore) {
        this.restore = restore;
    }

    static <T> NbtTearScope enter(ThreadLocal<T> local, T value) {
        T previous = local.get();
        set(local, value);
        return new NbtTearScope(() -> set(local, previous));
    }

    private static <T> void set(ThreadLocal<T> local, T value) {
        if (value == null) {
            local.remove();
        } else {
            local.set(value);
        }
    }

    @Override
    public void close() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("NBT tear context must be restored on its entering thread");
        }
        if (restore != null) {
            Runnable action = restore;
            restore = null;
            action.run();
        }
    }
}
