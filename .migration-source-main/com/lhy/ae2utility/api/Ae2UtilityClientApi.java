package com.lhy.ae2utility.api;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import com.lhy.ae2utility.jei.EncodePatternButtonController;

/**
 * 客户端专用查询入口，属于 {@code com.lhy.ae2utility.api} 兼容范围。
 *
 * <p>本类引用了客户端实现类，只允许在客户端环境（如 JEI 界面渲染路径）通过反射调用；
 * 专用服务端不会加载它，因此不会引入 JEI/AE2 客户端类的加载风险。
 */
public final class Ae2UtilityClientApi {
    private Ae2UtilityClientApi() {
    }

    /**
     * 当前玩家是否可以从 JEI 配方界面执行样板编码/上传，与 AE2 Utility 自己的 JEI 编码箭头
     * 可见性同源（打开样板编码类终端，或配置允许且携带可用无线样板终端）。
     *
     * <p>供其他模组（如 JEI Crafting Tree）在决定是否显示自己的入口时查询，避免两个入口同时出现。
     */
    public static boolean isJeiPatternEncodingAvailable() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        return EncodePatternButtonController.playerMayEncodePatterns(player);
    }
}