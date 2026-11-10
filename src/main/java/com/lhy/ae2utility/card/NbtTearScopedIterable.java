package com.lhy.ae2utility.card;

import java.util.Iterator;
import java.util.function.Supplier;

/** Keeps lazy predicates scoped even when iteration happens after the producer returns. */
public final class NbtTearScopedIterable {
    private NbtTearScopedIterable() {}

    public static <T> Iterable<T> wrap(Iterable<T> delegate, Supplier<NbtTearScope> scope) {
        return () -> {
            final Iterator<T> iterator;
            try (var ignored = scope.get()) {
                iterator = delegate.iterator();
            }
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    try (var ignored = scope.get()) {
                        return iterator.hasNext();
                    }
                }

                @Override
                public T next() {
                    try (var ignored = scope.get()) {
                        return iterator.next();
                    }
                }

                @Override
                public void remove() {
                    try (var ignored = scope.get()) {
                        iterator.remove();
                    }
                }
            };
        };
    }
}
