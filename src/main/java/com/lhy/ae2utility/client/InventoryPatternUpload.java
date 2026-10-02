package com.lhy.ae2utility.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.client.gui.me.items.PatternEncodingTermScreen;
import appeng.client.gui.style.WidgetStyle;

import com.lhy.ae2utility.jei.JeiBatchEncodeQueue;
import com.lhy.ae2utility.network.ModNetworking;
import com.lhy.ae2utility.network.UploadInventoryPatternStepPacket;
import com.lhy.ae2utility.network.UploadInventoryPatternsPacket;
import com.lhy.ae2utility.service.InventoryPatternUploadService;

/**
 * 样板编码终端里 Alt+左键 EAEP 上传按钮：批量上传背包中的全部已编码样板。
 */
public final class InventoryPatternUpload {
    private InventoryPatternUpload() {
    }

    public static boolean onMouseClicked(Screen screen, double mouseX, double mouseY, int button) {
        if (button != 0 || !Screen.hasAltDown() || Screen.hasControlDown() || Screen.hasShiftDown()
                || !(screen instanceof PatternEncodingTermScreen<?> termScreen)
                || !ModList.get().isLoaded("extendedae_plus") || !isOverUploadButton(termScreen, mouseX, mouseY)) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return true;
        }
        if (JeiBatchEncodeQueue.isActive()) {
            player.displayClientMessage(Objects.requireNonNull(
                    Component.translatable("message.ae2utility.batch_encode_session_busy")
                            .withStyle(ChatFormatting.GOLD)), true);
            return true;
        }
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack)) {
                slots.add(slot);
            }
        }
        if (slots.isEmpty()) {
            player.displayClientMessage(Objects.requireNonNull(
                    Component.translatable("message.ae2utility.inventory_upload_none")
                            .withStyle(ChatFormatting.RED)), true);
            return true;
        }
        ModNetworking.sendToServer(new UploadInventoryPatternsPacket(slots));
        return true;
    }

    public static void onRemaining(List<Integer> slots) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || slots.isEmpty()) {
            return;
        }
        Map<String, List<JeiBatchEncodeQueue.Entry>> groups = new LinkedHashMap<>();
        for (int slot : slots) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(stack)) {
                continue;
            }
            var tag = stack.getTag();
            String searchKey = tag != null ? tag.getString(InventoryPatternUploadService.SEARCH_KEY_TAG) : "";
            String label = patternName(player, stack);
            List<JeiBatchEncodeQueue.Entry> group = groups.computeIfAbsent(searchKey, k -> new ArrayList<>());
            for (int i = 0; i < stack.getCount(); i++) {
                group.add(new JeiBatchEncodeQueue.Entry(new UploadInventoryPatternStepPacket(slot), label, searchKey,
                        "inventory:" + searchKey));
            }
        }
        List<JeiBatchEncodeQueue.Entry> entries = groups.values().stream().flatMap(group -> group.stream()).toList();
        if (!JeiBatchEncodeQueue.startEntries(entries)) {
            player.displayClientMessage(Objects.requireNonNull(
                    Component.translatable("message.ae2utility.batch_encode_session_busy")
                            .withStyle(ChatFormatting.GOLD)), true);
        }
    }

    private static String patternName(LocalPlayer player, ItemStack stack) {
        try {
            var details = PatternDetailsHelper.decodePattern(stack, player.level());
            if (details != null && details.getOutputs().length > 0 && details.getOutputs()[0] != null) {
                return details.getOutputs()[0].what().getDisplayName().getString();
            }
        } catch (RuntimeException ignored) {
        }
        return stack.getHoverName().getString();
    }

    // EAEP 把上传按钮放在编码按钮左侧，大小为编码按钮的 0.75 倍。
    private static boolean isOverUploadButton(PatternEncodingTermScreen<?> screen, double mouseX, double mouseY) {
        try {
            WidgetStyle widget = screen.getStyle().getWidget("encodePattern");
            var pos = widget.resolve(new Rect2i(screen.getGuiLeft(), screen.getGuiTop(), screen.getXSize(),
                    screen.getYSize()));
            int baseWidth = widget.getWidth() > 0 ? widget.getWidth() : 16;
            int baseHeight = widget.getHeight() > 0 ? widget.getHeight() : 16;
            int width = Math.max(10, Math.round(baseWidth * 0.75f));
            int height = Math.max(10, Math.round(baseHeight * 0.75f));
            int x = pos.getX() - width;
            int y = pos.getY();
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
