package com.lhy.ae2utility.client;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

import com.mojang.blaze3d.platform.InputConstants;

import com.lhy.ae2utility.jei.JeiBookmarkUtil;

import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * 「清空全部 JEI 收藏」键位：{@link com.lhy.ae2utility.Ae2UtilityMod} 在模组总线的
 * {@link RegisterKeyMappingsEvent} 中注册绑定；清空动作在客户端总线的 {@link InputEvent.Key} 里完成
 * （避免 JEI 搜索框等 {@link EditBox} 吞键后 vanilla {@link KeyMapping#consumeClick} 永远不递增）。
 */
public final class Ae2UtilityKeyBindings {
    private static KeyMapping clearAllJeiBookmarks;

    private Ae2UtilityKeyBindings() {
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        clearAllJeiBookmarks = new KeyMapping(
                "key.ae2utility.clear_all_jei_bookmarks",
                KeyConflictContext.UNIVERSAL,
                KeyModifier.CONTROL,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_A,
                "key.categories.ae2utility");
        event.register(clearAllJeiBookmarks);
    }

    public static void onKey(InputEvent.Key event) {
        if (!Ae2UtilityClientConfig.enableClearAllJeiBookmarksHotkey()) {
            return;
        }
        if (clearAllJeiBookmarks == null || clearAllJeiBookmarks.isUnbound()) {
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (keyboardFocusedEditBoxConsumesShortcuts()) {
            return;
        }
        InputConstants.Key pressed = InputConstants.getKey(event.getKey(), event.getScanCode());
        if (clearAllJeiBookmarks.isActiveAndMatches(pressed)) {
            JeiBookmarkUtil.clearAllBookmarks();
        }
    }

    private static boolean keyboardFocusedEditBoxConsumesShortcuts() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen == null) {
            return false;
        }
        var focused = screen.getFocused();
        return focused instanceof EditBox eb && eb.isFocused();
    }
}
