package com.lhy.ae2utility.jei;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.runtime.IJeiRuntime;

/**
 * 1.20.1 forge：保存 JEI runtime 引用，供需要访问 JEI 运行时的逻辑读取。
 *
 * <p>Forge 1.20.1 的 JEI decorator 没有点击回调，因此这里缓存上一帧绘制的按钮区域，
 * 由 Forge 鼠标事件转发给 {@link EncodePatternButtonController}。</p>
 */
public final class EncodePatternButtonState {

    private EncodePatternButtonState() {
    }

    private static volatile @Nullable IJeiRuntime jeiRuntime;
    private static final List<ActiveButton> activeButtons = new ArrayList<>();
    private static int activeButtonsTtl;

    public static void setJeiRuntime(IJeiRuntime runtime) {
        jeiRuntime = runtime;
    }

    public static void clearJeiRuntime() {
        jeiRuntime = null;
        clearActiveButton();
    }

    public static @Nullable IJeiRuntime getJeiRuntime() {
        return jeiRuntime;
    }

    public static void setActiveButton(IRecipeLayoutDrawable<?> recipeLayout, int x, int y, int width, int height) {
        activeButtons.add(new ActiveButton(recipeLayout, x, y, width, height));
        activeButtonsTtl = 2;
    }

    public static void tick() {
        if (activeButtonsTtl > 0) {
            activeButtonsTtl--;
            if (activeButtonsTtl == 0) {
                clearActiveButton();
            }
        }
    }

    public static boolean pressIfHovered(double mouseX, double mouseY) {
        for (int i = activeButtons.size() - 1; i >= 0; i--) {
            ActiveButton button = activeButtons.get(i);
            if (button.contains(mouseX, mouseY)) {
                return EncodePatternButtonController.press(button.recipeLayout());
            }
        }
        return false;
    }

    public static void clearActiveButton() {
        activeButtons.clear();
        activeButtonsTtl = 0;
    }

    private record ActiveButton(IRecipeLayoutDrawable<?> recipeLayout, int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
