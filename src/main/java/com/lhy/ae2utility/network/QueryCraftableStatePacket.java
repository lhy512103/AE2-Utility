package com.lhy.ae2utility.network;

import java.util.ArrayList;
import java.util.List;

import appeng.api.stacks.AEKey;
import net.minecraft.network.FriendlyByteBuf;

public class QueryCraftableStatePacket {
    private final List<AEKey> keys;

    public QueryCraftableStatePacket(List<AEKey> keys) {
        this.keys = keys;
    }

    public List<AEKey> keys() {
        return keys;
    }

    public static void encode(QueryCraftableStatePacket msg, FriendlyByteBuf buffer) {
        buffer.writeVarInt(msg.keys.size());
        for (AEKey key : msg.keys) {
            AEKey.writeKey(buffer, key);
        }
    }

    public static QueryCraftableStatePacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<AEKey> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(AEKey.readKey(buffer));
        }
        return new QueryCraftableStatePacket(list);
    }
}
