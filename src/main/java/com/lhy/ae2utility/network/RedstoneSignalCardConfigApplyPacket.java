package com.lhy.ae2utility.network;

import com.lhy.ae2utility.card.RedstoneSignalCardMode;
import com.lhy.ae2utility.item.RedstoneSignalCardItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;

/** Client -> server card settings update. Register in ModNetworking (PLAY_TO_SERVER). */
public record RedstoneSignalCardConfigApplyPacket(boolean mainHand, int modeOrdinal, int holdTicks) {
    public static void encode(RedstoneSignalCardConfigApplyPacket msg, FriendlyByteBuf buf) { buf.writeBoolean(msg.mainHand); buf.writeVarInt(msg.modeOrdinal); buf.writeVarInt(msg.holdTicks); }
    public static RedstoneSignalCardConfigApplyPacket decode(FriendlyByteBuf buf) { return new RedstoneSignalCardConfigApplyPacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt()); }
    public static void applyOnServer(ServerPlayer player, RedstoneSignalCardConfigApplyPacket packet) {
        ItemStack stack=player.getItemInHand(packet.mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        if (!(stack.getItem() instanceof RedstoneSignalCardItem)) return;
        RedstoneSignalCardMode[] modes=RedstoneSignalCardMode.values();
        int ordinal=Math.max(0, Math.min(packet.modeOrdinal, modes.length-1));
        RedstoneSignalCardItem.setMode(stack, modes[ordinal]); RedstoneSignalCardItem.setHoldTicks(stack, packet.holdTicks);
    }
}
