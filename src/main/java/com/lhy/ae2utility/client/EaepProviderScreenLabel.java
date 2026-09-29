package com.lhy.ae2utility.client;

import java.lang.reflect.Field;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 在 ExtendedAE Plus 供应器选择界面的搜索框上方显示「正在处理样板 X（机器 Y）」。
 */
public final class EaepProviderScreenLabel {
    public static final String PROVIDER_SELECT_SCREEN = "com.extendedae_plus.client.screen.ProviderSelectScreen";

    private static @Nullable String patternName;
    private static @Nullable String machineName;
    private static @Nullable Field searchBoxField;
    private static boolean reflectionFailed;

    private EaepProviderScreenLabel() {
    }

    public static boolean isProviderSelectScreen(@Nullable Screen screen) {
        return screen != null && PROVIDER_SELECT_SCREEN.equals(screen.getClass().getName());
    }

    public static void setCurrent(@Nullable String pattern, @Nullable String machine) {
        patternName = pattern;
        machineName = machine;
    }

    public static void clear() {
        patternName = null;
        machineName = null;
    }

    public static void render(Screen screen, GuiGraphics guiGraphics) {
        if (patternName == null || patternName.isBlank() || !isProviderSelectScreen(screen)) {
            return;
        }
        EditBox searchBox = searchBox(screen);
        if (searchBox == null) {
            return;
        }
        var font = Minecraft.getInstance().font;
        String machine = machineName == null || machineName.isBlank() ? "-" : machineName;
        String text = Component.translatable("gui.ae2utility.eaep_processing_pattern", patternName, machine).getString();
        String trimmed = font.plainSubstrByWidth(text, Math.max(40, searchBox.getWidth() - 4));
        guiGraphics.drawString(font, trimmed, searchBox.getX() + 2, searchBox.getY() - 12, 0xFFFFFFFF);
    }

    private static @Nullable EditBox searchBox(Screen screen) {
        if (reflectionFailed) {
            return null;
        }
        try {
            if (searchBoxField == null) {
                Field field = screen.getClass().getDeclaredField("searchBox");
                field.setAccessible(true);
                searchBoxField = field;
            }
            return searchBoxField.get(screen) instanceof EditBox box ? box : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            return null;
        }
    }
}
