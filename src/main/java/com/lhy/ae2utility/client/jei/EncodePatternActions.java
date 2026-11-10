package com.lhy.ae2utility.client.jei;

import com.lhy.ae2utility.jei.JeiBookmarkUtil;
import com.lhy.ae2utility.jei.JeiEncodePacketFactory;

import com.lhy.ae2utility.compat.JeictCompat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.lhy.ae2utility.network.ModNetworking;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiUserInput;

/** Click actions are independent of query preparation and render snapshots. */
public final class EncodePatternActions {
    private EncodePatternActions() {}
    public static boolean dispatch(IRecipeLayoutDrawable<?> recipeLayout, IJeiUserInput input) {
        IRecipeSlotsView slotsView = recipeLayout.getRecipeSlotsView();

        boolean altJeict = JeictCompat.isLoaded()
                && Screen.hasAltDown() && !Screen.hasControlDown() && !Screen.hasShiftDown();
        boolean ctrlShiftUpload = JeiBookmarkUtil.isCtrlShiftLeftClickAnchor(input);
        boolean ctrlLeftBookmarkEncode = JeiBookmarkUtil.isCtrlLeftClickAnchor(input);
        if ((altJeict || ctrlShiftUpload || ctrlLeftBookmarkEncode) && input.isSimulate()) {
            return true;
        }

        if (!altJeict && !ctrlShiftUpload && !ctrlLeftBookmarkEncode && input.isSimulate()) {
            return false;
        }

        if (altJeict) {
            JeictCompat.openFromLayout(recipeLayout, Minecraft.getInstance().screen);
            return true;
        }

        if (ctrlShiftUpload || ctrlLeftBookmarkEncode) {
            int added = JeiBookmarkUtil.bookmarkMissingCraftingInputs(slotsView);
            var player = Minecraft.getInstance().player;
            if (player != null) {
                if (added > 0) {
                    player.displayClientMessage(
                            Component.translatable("message.ae2utility.bookmark_missing_inputs", added).withStyle(ChatFormatting.WHITE),
                            true);
                } else {
                    player.displayClientMessage(
                            Component.translatable("message.ae2utility.bookmark_missing_inputs_none").withStyle(ChatFormatting.WHITE),
                            true);
                }
            }
        }

        boolean shiftDown = Screen.hasShiftDown();
        if (ctrlShiftUpload) {
            shiftDown = true;
        }
        final boolean shiftForPacket = shiftDown;

        JeiEncodePacketFactory.tryCreate(recipeLayout, shiftForPacket, false, 0).ifPresent(packet -> {
            ModNetworking.sendToServer(packet);
        });
        return true;
    }

}
