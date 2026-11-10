package com.lhy.ae2utility.network;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.client.InventoryPatternUploadQueue;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 背包样板写入 EAEP 供应器的结果确认；客户端在收到前应阻塞下一条以防供应器已满仍误推进队列。
 */
public record InventoryProviderUploadAckPacket(int slotIndex, boolean success)
        implements CustomPacketPayload {

    public static final Type<InventoryProviderUploadAckPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "inventory_provider_upload_ack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InventoryProviderUploadAckPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.slotIndex);
                buf.writeBoolean(p.success);
            },
            buf -> new InventoryProviderUploadAckPacket(buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<InventoryProviderUploadAckPacket> type() {
        return TYPE;
    }

    public static void handle(InventoryProviderUploadAckPacket packet) {
        InventoryPatternUploadQueue.handleUploadAck(packet.slotIndex(), packet.success());
    }
}
