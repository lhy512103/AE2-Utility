package com.lhy.ae2utility.emi;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.lhy.ae2utility.client.Ae2UtilityClientConfig;
import com.lhy.ae2utility.client.RemoteEncodeRules;
import com.lhy.ae2utility.jei.BlankPatternClientPrecheck;
import com.lhy.ae2utility.jei.BulkEncodeSessions;
import com.lhy.ae2utility.jei.CraftableStateCache;
import com.lhy.ae2utility.jei.EncodePatternButtonController;
import com.lhy.ae2utility.jei.JeiBatchEncodeQueue;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.ModNetworking;

import appeng.api.stacks.GenericStack;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.screen.RecipeScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * EMI counterpart of {@link com.lhy.ae2utility.jei.JeiRecipesBatchEncode}: batch encode /
 * upload every recipe on the current page, or across all pages of the focused category tab,
 * directly from EMI's recipe screen.
 */
public final class EmiRecipesBatchEncode {
    private EmiRecipesBatchEncode() {
    }

    public static void run(boolean currentPageOnly, boolean shiftUpload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || !(mc.screen instanceof RecipeScreen recipeScreen)) {
            return;
        }
        LocalPlayer player = mc.player;
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

        List<EmiRecipe> recipes = currentPageOnly
                ? EmiRecipeScreenAccess.getCurrentPageRecipes(recipeScreen)
                : EmiRecipeScreenAccess.getCurrentTabRecipes(recipeScreen);
        encode(player, recipes, !currentPageOnly, shiftUpload);
    }

    private static void encode(LocalPlayer player, List<EmiRecipe> recipes, boolean fullCategory,
            boolean shiftUpload) {
        int sessionId = BulkEncodeSessions.next();
        Map<EncodePatternPacket, String> machines = new IdentityHashMap<>();
        List<EncodePatternPacket> packets = filterExistingPatterns(collect(recipes, shiftUpload, machines));

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
        List<JeiBatchEncodeQueue.Entry> entries = new ArrayList<>(packets.size());
        for (EncodePatternPacket packet : packets) {
            EncodePatternPacket batchPacket = packet.withBatch(fullCategory, sessionId);
            tagged.add(batchPacket);
            entries.add(new JeiBatchEncodeQueue.Entry(batchPacket, batchPacket.patternName(),
                    machines.getOrDefault(packet, ""), null));
        }

        if (shiftUpload) {
            if (!JeiBatchEncodeQueue.startEntries(entries)) {
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

    private static void message(LocalPlayer player, String key, ChatFormatting color) {
        player.displayClientMessage(Component.translatable(key).withStyle(color), false);
    }

    private static List<EncodePatternPacket> collect(List<EmiRecipe> recipes, boolean shiftUpload,
            Map<EncodePatternPacket, String> machines) {
        List<EncodePatternPacket> out = new ArrayList<>();
        for (EmiRecipe recipe : recipes) {
            EmiEncodePacketFactory.tryCreate(recipe, shiftUpload)
                    .filter(EncodePatternPacket::fitsNetworkLimits)
                    .ifPresent(packet -> {
                        out.add(packet);
                        machines.put(packet, categoryName(recipe));
                    });
        }
        return out;
    }

    private static String categoryName(EmiRecipe recipe) {
        try {
            var category = recipe.getCategory();
            if (category != null && category.getName() != null) {
                String name = category.getName().getString();
                return name == null ? "" : name.trim();
            }
        } catch (Throwable ignored) {
        }
        return "";
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
