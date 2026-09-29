package com.lhy.ae2utility.jei;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.common.gui.JeiTooltip;

/**
 * 识别 JEI 配方里的概率产物槽位：读取槽位 tooltip 回调，出现「%/chance/概率/几率」即视为概率产物。
 * 结果按槽位对象缓存，JEI 每个配方布局都有自己的槽位实例。
 */
public final class StochasticOutputDetector {
    private static final Map<IRecipeSlotView, Boolean> CACHE = new WeakHashMap<>();
    private static @Nullable Field tooltipCallbacksField;
    private static boolean reflectionFailed;

    private StochasticOutputDetector() {
    }

    public static boolean isChanceOutput(@Nullable Object recipe, IRecipeSlotView slotView) {
        if (isMekanismSawmillSecondaryChance(recipe)) {
            return true;
        }
        return CACHE.computeIfAbsent(slotView, StochasticOutputDetector::detectFromTooltip);
    }

    private static boolean detectFromTooltip(IRecipeSlotView slotView) {
        List<?> callbacks = tooltipCallbacks(slotView);
        if (callbacks == null || callbacks.isEmpty()) {
            return false;
        }
        try {
            JeiTooltip tooltip = new JeiTooltip();
            for (Object callback : callbacks) {
                if (callback instanceof IRecipeSlotRichTooltipCallback rich) {
                    rich.onRichTooltip(slotView, tooltip);
                }
            }
            for (var line : tooltip.getLines()) {
                String text = line.left().map(formatted -> formatted.getString()).orElse("");
                if (text.isEmpty()) {
                    continue;
                }
                String lower = text.toLowerCase(Locale.ROOT);
                if (text.contains("%") || lower.contains("chance") || lower.contains("probab")
                        || text.contains("概率") || text.contains("几率")) {
                    return true;
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        return false;
    }

    private static @Nullable List<?> tooltipCallbacks(IRecipeSlotView slotView) {
        if (reflectionFailed) {
            return null;
        }
        try {
            if (tooltipCallbacksField == null || tooltipCallbacksField.getDeclaringClass() != slotView.getClass()) {
                Field field = slotView.getClass().getDeclaredField("tooltipCallbacks");
                field.setAccessible(true);
                tooltipCallbacksField = field;
            }
            return tooltipCallbacksField.get(slotView) instanceof List<?> list ? list : null;
        } catch (NoSuchFieldException e) {
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            return null;
        }
    }

    private static boolean isMekanismSawmillSecondaryChance(@Nullable Object recipe) {
        if (recipe == null) {
            return false;
        }
        boolean sawmill = false;
        for (Class<?> type = recipe.getClass(); type != null; type = type.getSuperclass()) {
            if ("mekanism.api.recipes.SawmillRecipe".equals(type.getName())) {
                sawmill = true;
                break;
            }
        }
        if (!sawmill) {
            return false;
        }
        try {
            Method getSecondaryChance = recipe.getClass().getMethod("getSecondaryChance");
            if (getSecondaryChance.invoke(recipe) instanceof Number chance) {
                return chance.doubleValue() > 0 && chance.doubleValue() < 1;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return false;
    }
}
