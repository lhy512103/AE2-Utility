package com.lhy.ae2utility.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import com.lhy.ae2utility.network.codec.StreamCodec;
import com.lhy.ae2utility.network.codec.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEKey;
import com.lhy.ae2utility.Ae2UtilityMod;

public record QueryCraftableStatePacket(long requestNonce, List<AEKey> keys) implements CustomPacketPayload {
    public QueryCraftableStatePacket(List<AEKey> keys) { this(0, keys); }
    public QueryCraftableStatePacket {
        NetworkValidation.requireSize(keys.size(), NetworkValidation.MAX_CRAFTABLE_KEYS, "keys");
        keys = List.copyOf(keys);
    }
    public static final CustomPacketPayload.Type<QueryCraftableStatePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "query_craftable_state"));

    public static final StreamCodec<FriendlyByteBuf, QueryCraftableStatePacket> STREAM_CODEC =
            StreamCodec.ofMember(QueryCraftableStatePacket::write, QueryCraftableStatePacket::decode);

    private static QueryCraftableStatePacket decode(FriendlyByteBuf buffer) {
        long nonce = buffer.readLong();
        int size = NetworkValidation.readListSize(buffer, NetworkValidation.MAX_CRAFTABLE_KEYS, "keys");
        List<AEKey> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(AEKey.readKey(buffer));
        }
        return new QueryCraftableStatePacket(nonce, list);
    }

    private void write(FriendlyByteBuf buffer) {
        buffer.writeLong(requestNonce);
        buffer.writeVarInt(keys.size());
        for (AEKey key : keys) {
            AEKey.writeKey(buffer, key);
        }
    }

    @Override
    public Type<QueryCraftableStatePacket> type() {
        return TYPE;
    }
}
