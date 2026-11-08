package com.lhy.ae2utility.network;

import net.minecraft.network.FriendlyByteBuf;
import com.lhy.ae2utility.network.codec.ByteBufCodecs;
import com.lhy.ae2utility.network.codec.StreamCodec;
import com.lhy.ae2utility.network.codec.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.card.RedstoneSignalCardMode;
import com.lhy.ae2utility.item.RedstoneSignalCardItem;

public record RedstoneSignalCardConfigApplyPacket(boolean mainHand, int modeOrdinal, int holdTicks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RedstoneSignalCardConfigApplyPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "redstone_signal_card_cfg"));

    public static final StreamCodec<FriendlyByteBuf, RedstoneSignalCardConfigApplyPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL,
                    RedstoneSignalCardConfigApplyPacket::mainHand,
                    ByteBufCodecs.VAR_INT,
                    RedstoneSignalCardConfigApplyPacket::modeOrdinal,
                    ByteBufCodecs.VAR_INT,
                    RedstoneSignalCardConfigApplyPacket::holdTicks,
                    RedstoneSignalCardConfigApplyPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void applyOnServer(Player player, RedstoneSignalCardConfigApplyPacket packet) {
        InteractionHand hand = packet.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof RedstoneSignalCardItem)) {
            return;
        }
        RedstoneSignalCardMode[] modes = RedstoneSignalCardMode.values();
        int mi = Mth.clamp(packet.modeOrdinal(), 0, modes.length - 1);
        com.lhy.ae2utility.item.RedstoneSignalCardItem.setMode(stack, modes[mi]);
        int ticks = RedstoneSignalCardItem.clampHoldTicks(packet.holdTicks());
        com.lhy.ae2utility.item.RedstoneSignalCardItem.setHoldTicks(stack, ticks);
    }
}
