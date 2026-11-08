package com.lhy.ae2utility.network.codec;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Small version-independent codec used by the port's Forge packets. */
public interface StreamCodec<B, V> {
    void encode(B buffer, V value);
    V decode(B buffer);

    static <B,V> StreamCodec<B,V> of(BiConsumer<B,V> writer, Function<B,V> reader) {
        return new StreamCodec<>() {
            public void encode(B b,V v) { writer.accept(b,v); }
            public V decode(B b) { return reader.apply(b); }
        };
    }
    static <B,V> StreamCodec<B,V> ofMember(BiConsumer<V,B> writer, Function<B,V> reader) {
        return of((b,v)->writer.accept(v,b),reader);
    }
    static <B,T,V> StreamCodec<B,V> composite(StreamCodec<? super B,T> a, Function<V,T> get, Function<T,V> make) {
        return of((b,v)->a.encode(b,get.apply(v)),b->make.apply(a.decode(b)));
    }
    static <B,T,U,V> StreamCodec<B,V> composite(StreamCodec<? super B,T> a, Function<V,T> ga,
            StreamCodec<? super B,U> c, Function<V,U> gc, BiFunction<T,U,V> make) {
        return of((b,v)->{a.encode(b,ga.apply(v));c.encode(b,gc.apply(v));},b->make.apply(a.decode(b),c.decode(b)));
    }
    @FunctionalInterface interface TriFunction<T,U,W,V> { V apply(T a,U b,W c); }
    static <B,T,U,W,V> StreamCodec<B,V> composite(StreamCodec<? super B,T> a, Function<V,T> ga,
            StreamCodec<? super B,U> c, Function<V,U> gc, StreamCodec<? super B,W> d, Function<V,W> gd,
            TriFunction<T,U,W,V> make) {
        return of((b,v)->{a.encode(b,ga.apply(v));c.encode(b,gc.apply(v));d.encode(b,gd.apply(v));},
                b->make.apply(a.decode(b),c.decode(b),d.decode(b)));
    }
    default <R> StreamCodec<B,R> apply(Function<StreamCodec<B,V>,StreamCodec<B,R>> factory) {
        return factory.apply(this);
    }
}
