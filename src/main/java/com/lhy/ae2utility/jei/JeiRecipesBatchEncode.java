package com.lhy.ae2utility.jei;

import java.util.ArrayList;
import java.util.List;

import com.lhy.ae2utility.client.Ae2UtilityClientConfig;
import com.lhy.ae2utility.client.RemoteEncodeRules;
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
            message(player, "message.ae2utility.batch_encode_no_terminal", ChatFormatting.RED);
            return;
        }
        if (!currentPageOnly && !Ae2UtilityClientConfig.showJeiBatchEncodeFullCategoryButton()) {
            message(player, "message.ae2utility.batch_encode_category_disabled_config", ChatFormatting.GOLD);
            return;
        }
        if (!currentPageOnly && RemoteEncodeRules.blocksFullCategoryBatch()) {
            message(player, "message.ae2utility.encode_rejected_full_category_blocked_server", ChatFormatting.RED);
            return;
        }

        List<IRecipeLayoutDrawable<?>> layouts = currentPageOnly
                ? JeiRecipesGuiAccess.getVisibleRecipeLayouts(screen)
                : JeiRecipesGuiAccess.createSelectedCategoryRecipeLayouts(screen);
        encode(player, layouts, !currentPageOnly, shiftUpload);
    }

    /** 批量编码任意 JEI 配方（如配方查找器所选），不属于整类批量。 */
    public static void run(List<IRecipeLayoutDrawable<?>> layouts, boolean shiftUpload) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (!EncodePatternButtonController.playerMayEncodePatterns(player)) {
            message(player, "message.ae2utility.batch_encode_no_terminal", ChatFormatting.RED);
            return;
        }
        encode(player, layouts, false, shiftUpload);
    }

    private static void encode(LocalPlayer player, List<IRecipeLayoutDrawable<?>> layouts, boolean fullCategory,
            boolean shiftUpload) {
        int sessionId = BulkEncodeSessions.next();
        List<EncodePatternPacket> packets = filterExistingPatterns(collect(layouts, shiftUpload));

        int collected = packets.size();
        packets = RemoteEncodeRules.capToSessionLimit(packets);
        if (packets.size() < collected) {
            player.displayClientMessage(Component.translatable(
                    "message.ae2utility.bulk_encode_truncated_client_notice", collected, packets.size())
                    .withStyle(ChatFormatting.GOLD), false);
        }

        if (packets.isEmpty()) {
            message(player, "message.ae2utility.batch_encode_none", ChatFormatting.GRAY);
            return;
        }

        if (BlankPatternClientPrecheck.lacksAnyDetectableBlankPattern(player)) {
            message(player, "message.ae2utility.batch_encode_precheck_no_blank", ChatFormatting.GOLD);
            return;
        }

        List<EncodePatternPacket> tagged = new ArrayList<>(packets.size());
        for (EncodePatternPacket packet : packets) {
            tagged.add(packet.withBatch(fullCategory, sessionId));
        }

        if (shiftUpload) {
            String machine = commonCategoryTitle(layouts);
            if (!JeiBatchEncodeQueue.start(tagged, machine)) {
                message(player, "message.ae2utility.batch_encode_session_busy", ChatFormatting.GOLD);
                return;
            }
            player.displayClientMessage(Component.translatable("message.ae2utility.batch_encode_queued", tagged.size())
                    .withStyle(ChatFormatting.GREEN), false);
            return;
        }

        for (EncodePatternPacket packet : tagged) {
            ModNetworking.sendToServer(packet);
        }
        player.displayClientMessage(Component.translatable("message.ae2utility.batch_encode_sent", tagged.size())
                .withStyle(ChatFormatting.GREEN), false);
    }

    private static String commonCategoryTitle(List<IRecipeLayoutDrawable<?>> layouts) {
        String machine = null;
        for (IRecipeLayoutDrawable<?> layout : layouts) {
            String title = EncodePatternButtonController.categoryTitle(layout);
            if (machine != null && !machine.equals(title)) {
                return "";
            }
            machine = title;
        }
        return machine == null ? "" : machine;
    }

    private static void message(LocalPlayer player, String key, ChatFormatting color) {
        player.displayClientMessage(Component.translatable(key).withStyle(color), false);
    }

    private static List<EncodePatternPacket> collect(List<IRecipeLayoutDrawable<?>> layouts, boolean shiftUpload) {
        List<EncodePatternPacket> out = new ArrayList<>();
        for (IRecipeLayoutDrawable<?> layout : layouts) {
            if (!JeiRecipePageKinds.isEncodableRecipeLayout(layout)) {
                continue;
            }
            JeiEncodePacketFactory.tryCreate(layout, shiftUpload)
                    .filter(EncodePatternPacket::fitsNetworkLimits)
                    .ifPresent(out::add);
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
