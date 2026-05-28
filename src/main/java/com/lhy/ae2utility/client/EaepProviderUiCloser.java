package com.lhy.ae2utility.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * EAEP {@code beginPendingCtrlQUpload} 会先打开供应器映射界面再尝试
 * {@code uploadPendingCtrlQPattern} 时，成功上传后界面可能仍停留在最顶层；收到成功结果时关一层。
 */
public final class EaepProviderUiCloser {
    private static final String EAEP_PROVIDER_SELECT = "com.extendedae_plus.client.screen.ProviderSelectScreen";

    private EaepProviderUiCloser() {}

    public static void closeProviderSelectIfTop() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Screen top = mc.screen;
        if (top == null) {
            return;
        }
        if (!EAEP_PROVIDER_SELECT.equals(top.getClass().getName())) {
            return;
        }
        mc.popGuiLayer();
    }
}