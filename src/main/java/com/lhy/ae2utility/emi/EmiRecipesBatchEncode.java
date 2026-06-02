package com.lhy.ae2utility.emi;

import java.util.ArrayList;
import java.util.List;

import com.lhy.ae2utility.client.Ae2UtilityClientConfig;
import com.lhy.ae2utility.client.RecipeTreeUploadQueue;
import com.lhy.ae2utility.client.RemoteEncodeRules;
import com.lhy.ae2utility.client.jei.BlankPatternClientPrecheck;
import com.lhy.ae2utility.debug.JeiEncodeQueueDebugLog;
import com.lhy.ae2utility.jei.BulkEncodeSessions;
import com.lhy.ae2utility.jei.CraftableStateCache;
import com.lhy.ae2utility.jei.EncodePatternButtonController;
import com.lhy.ae2utility.network.EncodePatternPacket;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.screen.RecipeScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

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
            player.displayClientMessage(
                    Component.translatable("message.ae2utility.batch_encode_no_terminal").withStyle(ChatFormatting.RED),
                    false);
            return;
        }
        if (!currentPageOnly && !Ae2UtilityClientConfig.showJeiBatchEncodeFullCategoryButton()) {
            player.displayClientMessage(
                    Component.translatable("message.ae2utility.batch_encode_category_disabled_config").withStyle(ChatFormatting.GOLD),
                    false);
            return;
        }
        if (!currentPageOnly && RemoteEncodeRules.remoteBlocksJeFullCategoryBatch()) {
            player.displayClientMessage(
                    Component.translatable("message.ae2utility.encode_rejected_full_category_blocked_server").withStyle(ChatFormatting.RED),
                    false);
            return;
        }

        int bulkSid = BulkEncodeSessions.next();
        boolean fullCategory = !currentPageOnly;
        List<EmiRecipe> recipes = currentPageOnly
                ? EmiRecipeScreenAccess.getCurrentPageRecipes(recipeScreen)
                : EmiRecipeScreenAccess.getCurrentTabRecipes(recipeScreen);

        List<EncodePatternPacket> packets = new ArrayList<>();
        for (EmiRecipe recipe : recipes) {
            EmiEncodePacketFactory.tryCreate(recipe, shiftUpload)
                    .map(base -> rebuild(base, shiftUpload, fullCategory, bulkSid))
                    .ifPresent(packets::add);
        }

        packets = filterExistingPatterns(packets);

        JeiEncodeQueueDebugLog.info("EmiRecipesBatchEncode.run collected {} packets (currentPageOnly={} shiftUpload={})",
                packets.size(), currentPageOnly, shiftUpload);

        int origCount = packets.size();
        packets = RemoteEncodeRules.capPacketsToServerBulkLimit(packets);
        if (packets.size() < origCount) {
            player.displayClientMessage(
                    Component.translatable("message.ae2utility.bulk_encode_truncated_client_notice", origCount, packets.size())
                            .withStyle(ChatFormatting.GOLD),
                    false);
        }

        if (packets.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.ae2utility.batch_encode_none").withStyle(ChatFormatting.GRAY),
                    false);
            return;
        }

        if (BlankPatternClientPrecheck.lacksAnyDetectableBlankPattern(player)) {
            player.displayClientMessage(
                    Component.translatable("message.ae2utility.batch_encode_precheck_no_blank").withStyle(ChatFormatting.GOLD),
                    false);
            return;
        }

        if (shiftUpload) {
            List<EncodePatternPacket> queued = new ArrayList<>(packets.size());
            for (EncodePatternPacket p : packets) {
                queued.add(new EncodePatternPacket(p.inputs(), p.outputs(), p.recipeId(), p.patternName(), p.providerSearchKey(),
                        p.providerDisplayName(), p.shiftDown(), p.substitute(), p.substituteFluids(), p.preserveInputOrder(), true,
                        p.jeiFullCategoryBatch(), p.bulkEncodeSessionId(), p.craftingCategoryHint()));
            }
            if (!RecipeTreeUploadQueue.start(queued)) {
                player.displayClientMessage(
                        Component.translatable("message.ae2utility.batch_encode_session_busy").withStyle(ChatFormatting.GOLD),
                        false);
                return;
            }
        } else {
            for (EncodePatternPacket packet : packets) {
                PacketDistributor.sendToServer(packet);
            }
            player.displayClientMessage(
                    Component.translatable("message.ae2utility.batch_encode_sent", packets.size()).withStyle(ChatFormatting.GREEN),
                    false);
        }
    }

    private static EncodePatternPacket rebuild(EncodePatternPacket base, boolean shiftUpload, boolean fullCategory, int bulkSid) {
        return new EncodePatternPacket(base.inputs(), base.outputs(), base.recipeId(), base.patternName(), base.providerSearchKey(),
                base.providerDisplayName(), shiftUpload, base.substitute(), base.substituteFluids(), base.preserveInputOrder(),
                false, fullCategory, bulkSid, base.craftingCategoryHint());
    }

    private static List<EncodePatternPacket> filterExistingPatterns(List<EncodePatternPacket> packets) {
        List<EncodePatternPacket> filtered = new ArrayList<>(packets.size());
        for (EncodePatternPacket packet : packets) {
            if (packet == null || packet.outputs().isEmpty() || packet.outputs().getFirst() == null
                    || packet.outputs().getFirst().what() == null) {
                continue;
            }
            if (CraftableStateCache.isCraftable(packet.outputs().getFirst().what())) {
                JeiEncodeQueueDebugLog.info("EmiRecipesBatchEncode.skip existing recipeId={}", packet.recipeId());
                continue;
            }
            filtered.add(packet);
        }
        return filtered;
    }
}
