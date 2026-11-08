package com.lhy.ae2utility.network.codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ByteBufCodecs {
    private ByteBufCodecs() {}
    public static final StreamCodec<ByteBuf,Integer> INT = StreamCodec.of(ByteBuf::writeInt,ByteBuf::readInt);
    public static final StreamCodec<ByteBuf,Integer> VAR_INT = StreamCodec.of(
            (b,v)->new FriendlyByteBuf(b).writeVarInt(v),b->new FriendlyByteBuf(b).readVarInt());
    public static final StreamCodec<ByteBuf,Boolean> BOOL = StreamCodec.of(ByteBuf::writeBoolean,ByteBuf::readBoolean);
    public static <B,V> Function<StreamCodec<B,V>,StreamCodec<B,List<V>>> list() {
        return codec -> StreamCodec.of((b,values)->{
            if(values.size()>4096) throw new IllegalArgumentException("List too large");
            VAR_INT.encode((ByteBuf)b,values.size());
            for(V v:values) codec.encode(b,v);
        },b->{
            int size=VAR_INT.decode((ByteBuf)b);
            if(size<0 || size>4096) throw new IllegalArgumentException("Invalid list size");
            List<V> values=new ArrayList<>(size);
            for(int i=0;i<size;i++) values.add(codec.decode(b));
            return values;
        });
    }
}
