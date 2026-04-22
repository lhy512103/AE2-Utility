package com.lhy.ae2utility.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEKey;
import com.lhy.ae2utility.Ae2UtilityMod;

/** 编码/上传样板成功后，令客户端丢弃这些材料的可合成缓存并尽快重查。 */
public record InvalidateCraftableCachePacket(List<AEKey> keys) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<InvalidateCraftableCachePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "invalidate_craftable_cache"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InvalidateCraftableCachePacket> STREAM_CODEC =
            StreamCodec.ofMember(InvalidateCraftableCachePacket::write, InvalidateCraftableCachePacket::decode);

    private static InvalidateCraftableCachePacket decode(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<AEKey> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(AEKey.readKey(buffer));
        }
        return new InvalidateCraftableCachePacket(list);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(keys.size());
        for (AEKey key : keys) {
            AEKey.writeKey(buffer, key);
        }
    }

    @Override
    public Type<InvalidateCraftableCachePacket> type() {
        return TYPE;
    }
}
