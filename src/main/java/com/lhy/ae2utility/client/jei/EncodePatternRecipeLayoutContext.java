package com.lhy.ae2utility.client.jei;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipesGui;

import com.lhy.ae2utility.mixin.RecipeGuiLayoutsAccessor;
import com.lhy.ae2utility.mixin.RecipesGuiAccessor;

/**
 * JEI 配方栏在 tick/draw 期间的线程上下文，用于把轮换 / Shift 固定限定到当前这一条配方。
 * <p>
 * 必须用<strong>栈</strong>而非单次 {@link ThreadLocal#set}：{@link mezz.jei.library.gui.recipes.RecipeLayout#tick}
 * 可能在 widget.tick 等路径中间接触发嵌套的 layout tick/draw，旧实现 push 覆盖 + {@code remove()} 清空会导致
 * {@link mezz.jei.library.gui.ingredients.CycleTicker#tick} 见到 {@code null}，悬停冻结轮转失效（WCWT 等复杂界面更易触发）。
 */
public final class EncodePatternRecipeLayoutContext {
    private static final ThreadLocal<Deque<IRecipeLayoutDrawable<?>>> ACTIVE_STACK =
            ThreadLocal.withInitial(() -> new ArrayDeque<>(4));

    private EncodePatternRecipeLayoutContext() {
    }

    public static void push(IRecipeLayoutDrawable<?> layout) {
        ACTIVE_STACK.get().addFirst(layout);
    }

    public static void pop() {
        Deque<IRecipeLayoutDrawable<?>> stack = ACTIVE_STACK.get();
        if (!stack.isEmpty()) {
            stack.removeFirst();
        }
        if (stack.isEmpty()) {
            ACTIVE_STACK.remove();
        }
    }

    public static @Nullable IRecipeLayoutDrawable<?> get() {
        Deque<IRecipeLayoutDrawable<?>> stack = ACTIVE_STACK.get();
        return stack.isEmpty() ? null : stack.peekFirst();
    }

    public static boolean slotBelongsToLayout(IRecipeLayoutDrawable<?> layout, IRecipeSlotView slot) {
        if (layout == null || slot == null) {
            return false;
        }
        for (RecipeIngredientRole role : RecipeIngredientRole.values()) {
            for (IRecipeSlotView v : layout.getRecipeSlotsView().getSlotViews(role)) {
                if (v == slot) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Tooltip 往往在 {@link #push}/{@link #pop} 之外调用 {@code getDisplayedIngredient}：此处在线程上下文为空时，
     * 根据当前打开的 {@link RecipesGui} 遍历可见配方栏，找回包含该槽且满足 Shift 编码预览条件的 layout。
     */
    public static @Nullable IRecipeLayoutDrawable<?> resolveLayoutForEncodeSlotTooltip(IRecipeSlotView slot) {
        if (slot == null) {
            return null;
        }
        if (!EncodePatternPreviewStorage.isShiftPinIngredientOverrideActive()) {
            IRecipeLayoutDrawable<?> onlyLayout = get();
            return onlyLayout != null && slotBelongsToLayout(onlyLayout, slot) ? onlyLayout : null;
        }
        IRecipeLayoutDrawable<?> active = get();
        if (active != null && slotBelongsToLayout(active, slot)) {
            return active;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !(mc.screen instanceof RecipesGui gui)) {
            return null;
        }
        if (!EncodePatternPreviewStorage.isPatternEncodingRecipesGui()) {
            return null;
        }
        RecipeGuiLayouts layouts = ((RecipesGuiAccessor) (Object) gui).ae2utility$layouts();
        List<?> rows = ((RecipeGuiLayoutsAccessor) (Object) layouts).ae2utility$recipeLayoutsWithButtons();
        for (Object rowObj : rows) {
            if (!(rowObj instanceof IRecipeLayoutWithButtons<?> row)) {
                continue;
            }
            IRecipeLayoutDrawable<?> layout = row.getRecipeLayout();
            if (!slotBelongsToLayout(layout, slot)) {
                continue;
            }
            if (!EncodePatternPreviewStorage.shouldPinDisplayedIngredient(layout, slot)) {
                continue;
            }
            return layout;
        }
        return null;
    }
}
