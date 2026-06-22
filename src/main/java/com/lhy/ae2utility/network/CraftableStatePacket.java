package com.lhy.ae2utility.network;

import java.util.ArrayList;
import java.util.List;

import appeng.api.stacks.AEKey;
import net.minecraft.network.FriendlyByteBuf;

public class CraftableStatePacket {
    private final List<AEKey> craftableKeys;
    private final List<AEKey> uncraftableKeys;

    public CraftableStatePacket(List<AEKey> craftableKeys, List<AEKey> uncraftableKeys) {
        this.craftableKeys = craftableKeys;
        this.uncraftableKeys = uncraftableKeys;
    }

    public List<AEKey> craftableKeys() {
        return craftableKeys;
    }

    public List<AEKey> uncraftableKeys() {
        return uncraftableKeys;
    }

    public static void encode(CraftableStatePacket msg, FriendlyByteBuf buffer) {
        buffer.writeVarInt(msg.craftableKeys.size());
        for (AEKey key : msg.craftableKeys) {
            AEKey.writeKey(buffer, key);
        }
        buffer.writeVarInt(msg.uncraftableKeys.size());
        for (AEKey key : msg.uncraftableKeys) {
            AEKey.writeKey(buffer, key);
        }
    }

    public static CraftableStatePacket decode(FriendlyByteBuf buffer) {
        int craftableSize = buffer.readVarInt();
        List<AEKey> craftableKeys = new ArrayList<>(craftableSize);
        for (int i = 0; i < craftableSize; i++) {
            craftableKeys.add(AEKey.readKey(buffer));
        }
        int uncraftableSize = buffer.readVarInt();
        List<AEKey> uncraftableKeys = new ArrayList<>(uncraftableSize);
        for (int i = 0; i < uncraftableSize; i++) {
            uncraftableKeys.add(AEKey.readKey(buffer));
        }
        return new CraftableStatePacket(craftableKeys, uncraftableKeys);
    }
}
