package com.lhy.ae2utility.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.lhy.ae2utility.client.Ae2UtilityClientConfig;
import com.lhy.ae2utility.client.RemoteEncodeRules;
import com.lhy.ae2utility.client.RecipeTreeUploadQueue;
import com.lhy.ae2utility.jei.CraftableStateCache;
import com.lhy.ae2utility.debug.JeiEncodeQueueDebugLog;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.ILookupState;

import com.lhy.ae2utility.client.jei.BlankPatternClientPrecheck;
import com.lhy.ae2utility.mixin.RecipeGuiLayoutsAccessor;
import com.lhy.ae2utility.mixin.RecipeGuiLogicAccessor;
import com.lhy.ae2utility.mixin.RecipesGuiAccessor;
import com.lhy.ae2utility.network.EncodePatternPacket;

public final class JeiRecipesBatchEncode {
    private JeiRecipesBatchEncode() {
    }

    public static void run(boolean currentPageOnly, boolean shiftUpload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || !(mc.screen instanceof RecipesGui recipesGui)) {
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
        List<EncodePatternPacket> packets = currentPageOnly
                ? collectCurrentPage(recipesGui, shiftUpload, bulkSid, false)
                : collectFullCategory(recipesGui, shiftUpload, bulkSid, true);

        packets = filterExistingPatterns(packets);

        JeiEncodeQueueDebugLog.info("JeiRecipesBatchEncode.run collected {} packets (currentPageOnly={} shiftUpload={})",
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
                        p.jeiFullCategoryBatch(), p.bulkEncodeSessionId()));
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
        }
        if (!shiftUpload) {
            player.displayClientMessage(
                    Component.translatable("message.ae2utility.batch_encode_sent", packets.size()).withStyle(ChatFormatting.GREEN),
                    false);
        }
    }

    private static List<EncodePatternPacket> collectCurrentPage(RecipesGui gui, boolean shiftUpload, int bulkEncodeSessionId,
            boolean jeiFullCategoryBatch) {
        List<EncodePatternPacket> out = new ArrayList<>();
        RecipeGuiLayouts layouts = ((RecipesGuiAccessor) (Object) gui).ae2utility$layouts();
        List<?> rows = ((RecipeGuiLayoutsAccessor) (Object) layouts).ae2utility$recipeLayoutsWithButtons();
        for (Object rowObj : rows) {
            if (!(rowObj instanceof IRecipeLayoutWithButtons<?> row)) {
                continue;
            }
            JeiEncodePacketFactory.tryCreate(row.getRecipeLayout(), shiftUpload, jeiFullCategoryBatch, bulkEncodeSessionId).ifPresent(out::add);
        }
        return out;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static List<EncodePatternPacket> collectFullCategory(RecipesGui gui, boolean shiftUpload, int bulkEncodeSessionId,
            boolean jeiFullCategoryBatch) {
        List<EncodePatternPacket> out = new ArrayList<>();
        IJeiRuntime runtime = Ae2UtilityJeiPlugin.getJeiRuntime();
        if (runtime == null) {
            return out;
        }

        ILookupState state = ((RecipeGuiLogicAccessor) (Object) ((RecipesGuiAccessor) (Object) gui).ae2utility$recipeLogic()).ae2utility$lookupState();
        IFocusedRecipes<?> focused = state.getFocusedRecipes();
        IRecipeCategory<?> category = focused.getRecipeCategory();
        List<?> recipes = focused.getRecipes();

        var emptyFocus = runtime.getJeiHelpers().getFocusFactory().createFocusGroup(List.of());

        for (Object recipe : recipes) {
            Optional<IRecipeLayoutDrawable<?>> layoutOpt =
                    (Optional) runtime.getRecipeManager().createRecipeLayoutDrawable((IRecipeCategory) category, recipe, emptyFocus);
            layoutOpt.flatMap(layout -> JeiEncodePacketFactory.tryCreate(layout, shiftUpload, jeiFullCategoryBatch, bulkEncodeSessionId))
                    .ifPresent(out::add);
        }
        return out;
    }

    private static List<EncodePatternPacket> filterExistingPatterns(List<EncodePatternPacket> packets) {
        List<EncodePatternPacket> filtered = new ArrayList<>(packets.size());
        for (EncodePatternPacket packet : packets) {
            if (packet == null || packet.outputs().isEmpty() || packet.outputs().getFirst() == null
                    || packet.outputs().getFirst().what() == null) {
                continue;
            }
            if (CraftableStateCache.isCraftable(packet.outputs().getFirst().what())) {
                JeiEncodeQueueDebugLog.info("JeiRecipesBatchEncode.skip existing recipeId={}", packet.recipeId());
                continue;
            }
            filtered.add(packet);
        }
        return filtered;
    }
}
