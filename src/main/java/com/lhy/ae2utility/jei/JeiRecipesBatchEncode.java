package com.lhy.ae2utility.jei;

import java.util.ArrayList;
import java.util.List;

import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.ModNetworking;

import appeng.api.stacks.GenericStack;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class JeiRecipesBatchEncode {
    private JeiRecipesBatchEncode() {
    }

    public static void run(boolean currentPageOnly, boolean shiftUpload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.screen == null || minecraft.player == null) {
            return;
        }

        Screen screen = minecraft.screen;
        LocalPlayer player = minecraft.player;
        if (!EncodePatternButtonController.playerMayEncodePatterns(player)) {
            player.displayClientMessage(
                    Component.translatable("message.ae2utility.batch_encode_no_terminal").withStyle(ChatFormatting.RED),
                    false);
            return;
        }

        List<EncodePatternPacket> packets = currentPageOnly
                ? collectCurrentPage(screen, shiftUpload)
                : collectSelectedCategory(screen, shiftUpload);
        packets = filterExistingPatterns(packets);

        if (packets.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.ae2utility.batch_encode_none").withStyle(ChatFormatting.GRAY),
                    false);
            return;
        }

        if (shiftUpload) {
            if (!JeiBatchEncodeQueue.start(packets)) {
                player.displayClientMessage(
                        Component.translatable("message.ae2utility.batch_encode_session_busy")
                                .withStyle(ChatFormatting.GOLD),
                        false);
                return;
            }
            player.displayClientMessage(
                    Component.translatable("message.ae2utility.batch_encode_queued", packets.size())
                            .withStyle(ChatFormatting.GREEN),
                    false);
            return;
        }

        for (EncodePatternPacket packet : packets) {
            ModNetworking.sendToServer(packet);
        }
        player.displayClientMessage(
                Component.translatable("message.ae2utility.batch_encode_sent", packets.size())
                        .withStyle(ChatFormatting.GREEN),
                false);
    }

    private static List<EncodePatternPacket> collectCurrentPage(Screen screen, boolean shiftUpload) {
        return collectFromLayouts(JeiRecipesGuiAccess.getVisibleRecipeLayouts(screen), shiftUpload);
    }

    private static List<EncodePatternPacket> collectSelectedCategory(Screen screen, boolean shiftUpload) {
        return collectFromLayouts(JeiRecipesGuiAccess.createSelectedCategoryRecipeLayouts(screen), shiftUpload);
    }

    private static List<EncodePatternPacket> collectFromLayouts(List<IRecipeLayoutDrawable<?>> layouts,
            boolean shiftUpload) {
        List<EncodePatternPacket> out = new ArrayList<>();
        for (IRecipeLayoutDrawable<?> layout : layouts) {
            if (layout == null || EncodePatternButtonController.isTagRecipe(layout.getRecipeCategory())) {
                continue;
            }
            JeiEncodePacketFactory.tryCreate(layout, shiftUpload).ifPresent(out::add);
        }
        return out;
    }

    private static List<EncodePatternPacket> filterExistingPatterns(List<EncodePatternPacket> packets) {
        List<EncodePatternPacket> filtered = new ArrayList<>(packets.size());
        for (EncodePatternPacket packet : packets) {
            if (packet == null || packet.outputs().isEmpty()) {
                continue;
            }
            GenericStack output = packet.outputs().get(0);
            if (output != null && output.what() != null && CraftableStateCache.isCraftable(output.what())) {
                continue;
            }
            filtered.add(packet);
        }
        return filtered;
    }
}
