package com.lhy.ae2utility.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import appeng.api.stacks.AEKey;
import com.lhy.ae2utility.Ae2UtilityMod;

public record CraftableStatePacket(List<AEKey> craftableKeys, List<AEKey> uncraftableKeys) implements CustomPacketPayload {
    public CraftableStatePacket {
        NetworkValidation.requireSize(craftableKeys.size(), NetworkValidation.MAX_CRAFTABLE_KEYS, "craftableKeys");
        NetworkValidation.requireSize(uncraftableKeys.size(), NetworkValidation.MAX_CRAFTABLE_KEYS, "uncraftableKeys");
    }
    public static final CustomPacketPayload.Type<CraftableStatePacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "craftable_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftableStatePacket> STREAM_CODEC =
            StreamCodec.ofMember(CraftableStatePacket::write, CraftableStatePacket::decode);

    private static CraftableStatePacket decode(RegistryFriendlyByteBuf buffer) {
        int craftableSize = NetworkValidation.readListSize(buffer, NetworkValidation.MAX_CRAFTABLE_KEYS, "craftableKeys");
        List<AEKey> craftableKeys = new ArrayList<>(craftableSize);
        for (int i = 0; i < craftableSize; i++) {
            craftableKeys.add(AEKey.readKey(buffer));
        }
        int uncraftableSize = NetworkValidation.readListSize(buffer, NetworkValidation.MAX_CRAFTABLE_KEYS, "uncraftableKeys");
        List<AEKey> uncraftableKeys = new ArrayList<>(uncraftableSize);
        for (int i = 0; i < uncraftableSize; i++) {
            uncraftableKeys.add(AEKey.readKey(buffer));
        }
        return new CraftableStatePacket(craftableKeys, uncraftableKeys);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(craftableKeys.size());
        for (AEKey key : craftableKeys) {
            AEKey.writeKey(buffer, key);
        }
        buffer.writeVarInt(uncraftableKeys.size());
        for (AEKey key : uncraftableKeys) {
            AEKey.writeKey(buffer, key);
        }
    }

    @Override
    public Type<CraftableStatePacket> type() {
        return TYPE;
    }
}
