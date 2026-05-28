package com.lhy.ae2utility.client.jei;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;

import appeng.api.storage.MEStorage;
import appeng.api.storage.ITerminalHost;

import com.lhy.ae2utility.client.Ae2UtilityClientConfig;
import com.lhy.ae2utility.compat.WcwtCompat;
import com.lhy.ae2utility.jei.EncodePatternButtonController;
import com.lhy.ae2utility.service.WirelessTerminalContextResolver;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.gui.recipes.RecipesGui;

/**
 * 客户端解析「编码样板时服务端能看见的那份 ME 终端存储」，用于 Shift 预览与服务端选股对齐。
 */
public final class EncodePatternPreviewStorage {
    private static final boolean SHIFT_PIN_INGREDIENT_OVERRIDES_ENABLED = false;

    private EncodePatternPreviewStorage() {
    }

    /**
     * JEI 编码箭头上报的 {@link com.lhy.ae2utility.network.EncodePatternPacket} 使用 preserveInputOrder=false。
     */
    public static boolean jeiEncodePreserveInputOrder() {
        return false;
    }

    /**
     * JEI 配方界面是否启用「样板编码」预览行为（轮换冻结、INPUT 选股与 ME 对齐）。
     * <p>条件与 {@link EncodePatternButtonController#playerMayEncodePatterns}、编码箭头一致：
     * 父界面为样板编码/WCWT，或玩家已打开编码类菜单，或在客户端配置
     * {@link Ae2UtilityClientConfig#allowJeiPatternEncodeWithoutOpenTerminal} 允许时背包/Curios 中有无线编码终端（无需先打开该终端 GUI）。</p>
     */
    public static boolean isPatternEncodingRecipesGui() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || !(mc.screen instanceof RecipesGui recipesGui)) {
            return false;
        }
        if (WcwtCompat.isPatternEncodingLikeMenu(recipesGui.getParentContainerMenu())) {
            return true;
        }
        return EncodePatternButtonController.playerMayEncodePatterns(mc.player);
    }

    /**
     * 顺序与 {@link com.lhy.ae2utility.service.EncodePatternService#handle}：先当前打开的 MEStorageMenu，再背包/Curios 无线终端。
     */
    public static @Nullable MEStorage resolveMeStorage(@Nullable Minecraft mc) {
        if (mc == null || mc.player == null) {
            return null;
        }
        if (mc.screen instanceof RecipesGui recipesGui) {
            AbstractContainerMenu menu = recipesGui.getParentContainerMenu();
            ITerminalHost host = WcwtCompat.extractTerminalHost(menu);
            if (host != null) {
                return host.getInventory();
            }
        }
        var resolution = WirelessTerminalContextResolver.resolve(mc.player);
        if (resolution.isReady() && resolution.host() != null) {
            return resolution.host().getInventory();
        }
        return null;
    }

    private static double guiScaledMouseX(@Nullable Minecraft mc) {
        if (mc == null || mc.getWindow() == null) {
            return 0;
        }
        var win = mc.getWindow();
        return mc.mouseHandler.xpos() * win.getGuiScaledWidth() / Math.max(1, win.getScreenWidth());
    }

    private static double guiScaledMouseY(@Nullable Minecraft mc) {
        if (mc == null || mc.getWindow() == null) {
            return 0;
        }
        var win = mc.getWindow();
        return mc.mouseHandler.ypos() * win.getGuiScaledHeight() / Math.max(1, win.getScreenHeight());
    }

    /**
     * 已关闭：<strong>SHIFT 预览 / 轮换时在 INPUT 槽固定「与发包选股一致」的展示Ingredient</strong>（高替补配方下极卡）。<br>
     * {@link EncodePatternRecipeLayoutContext}、{@code MixinRecipeSlotEncodeShiftPin} 会先检查此方法再决定是否劫持。
     */
    public static boolean isShiftPinIngredientOverrideActive() {
        return SHIFT_PIN_INGREDIENT_OVERRIDES_ENABLED;
    }

    /** 占位：曾用于 CycleTicker「悬停箭头/槽冻结轮换」，当前与其它逻辑一并关闭时可恒为 false。 */
    public static boolean shouldFreezeIngredientCycleForLayout(@Nullable IRecipeLayoutDrawable<?> layout) {
        return SHIFT_PIN_INGREDIENT_OVERRIDES_ENABLED && shouldFreezeIngredientCycleIfEnabled(layout);
    }

    private static boolean shouldFreezeIngredientCycleIfEnabled(@Nullable IRecipeLayoutDrawable<?> layout) {
        if (layout == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        double mx = guiScaledMouseX(mc);
        double my = guiScaledMouseY(mc);
        EncodePatternButtonController ctrl = EncodePatternButtonController.CONTROLLERS.get(layout);
        boolean buttonHit = ctrl != null && ctrl.isMouseOverEncodeButton(mx, my);
        boolean slotHit = layout.getRecipeSlotUnderMouse(mx, my).isPresent();
        return buttonHit || slotHit;
    }

    /**
     * INPUT 槽展示是否按 ME 选股固定（与移位发送一致）。当前 {@link #SHIFT_PIN_INGREDIENT_OVERRIDES_ENABLED} 为 false，恒不固定。
     */
    public static boolean shouldPinDisplayedIngredient(@Nullable IRecipeLayoutDrawable<?> layout, @Nullable IRecipeSlotView slot) {
        if (!SHIFT_PIN_INGREDIENT_OVERRIDES_ENABLED) {
            return false;
        }
        if (layout == null || slot == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        double mx = guiScaledMouseX(mc);
        double my = guiScaledMouseY(mc);
        EncodePatternButtonController ctrl = EncodePatternButtonController.CONTROLLERS.get(layout);
        boolean buttonHit = ctrl != null && ctrl.isMouseOverEncodeButton(mx, my);
        boolean slotHit = layout.getRecipeSlotUnderMouse(mx, my).isPresent();
        return buttonHit || slotHit;
    }
}
