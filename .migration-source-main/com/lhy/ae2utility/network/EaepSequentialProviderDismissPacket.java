package com.lhy.ae2utility.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.lhy.ae2utility.Ae2UtilityMod;

/**
 * 客户端在关闭 EAEP 供应器选择界面后发送：服务端若仍存在顺序 Shift 待定上传，则尝试归还待上传样板并下发最终结果包以解冻 JEI 队列。
 */
public final class EaepSequentialProviderDismissPacket implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EaepSequentialProviderDismissPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID,
                    "eaep_shift_sequential_provider_dismiss"));

    public static final EaepSequentialProviderDismissPacket INSTANCE = new EaepSequentialProviderDismissPacket();

    public static final StreamCodec<RegistryFriendlyByteBuf, EaepSequentialProviderDismissPacket> STREAM_CODEC =
            StreamCodec.of((RegistryFriendlyByteBuf buf, EaepSequentialProviderDismissPacket p) -> {}, buf -> INSTANCE);

    private EaepSequentialProviderDismissPacket() {}

    @Override
    public CustomPacketPayload.Type<EaepSequentialProviderDismissPacket> type() {
        return TYPE;
    }
}
