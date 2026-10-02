package com.lhy.ae2utility.jei;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.lhy.ae2utility.Ae2UtilityMod;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.Screen;

/**
 * Centralizes the small amount of JEI 15 internal access needed by the Forge
 * 1.20.1 overlay path. JEI does not expose the current RecipesGui layout list or
 * selected lookup state through its public runtime API in this version.
 */
public final class JeiRecipesGuiAccess {
    private static final String RECIPES_GUI_CLASS = "mezz.jei.gui.recipes.RecipesGui";

    private static @Nullable Field layoutsField;
    private static @Nullable Field recipeLayoutsWithButtonsField;
    private static @Nullable Field logicField;
    private static @Nullable Field stateField;
    private static @Nullable Method recipeLayoutMethod;
    private static @Nullable Method focusedRecipesMethod;
    private static @Nullable Method recipesMethod;
    private static @Nullable Method recipeCategoryMethod;
    private static boolean reflectionFailed;

    private JeiRecipesGuiAccess() {
    }

    public static boolean isRecipesGui(@Nullable Screen screen) {
        return screen != null && RECIPES_GUI_CLASS.equals(screen.getClass().getName());
    }

    public static List<IRecipeLayoutDrawable<?>> getVisibleRecipeLayouts(Screen screen) {
        List<?> rows = getVisibleRecipeLayoutRows(screen);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<IRecipeLayoutDrawable<?>> result = new ArrayList<>(rows.size());
        for (Object row : rows) {
            IRecipeLayoutDrawable<?> recipeLayout = getRecipeLayout(row);
            if (recipeLayout != null) {
                result.add(recipeLayout);
            }
        }
        return result;
    }

    private static @Nullable List<?> getVisibleRecipeLayoutRows(Screen screen) {
        if (reflectionFailed || !isRecipesGui(screen)) {
            return null;
        }

        try {
            Field layouts = layoutsField;
            if (layouts == null) {
                layouts = declaredField(screen.getClass(), "layouts");
                layoutsField = layouts;
            }
            Object layoutsValue = layouts.get(screen);
            if (layoutsValue == null) {
                return null;
            }

            Field recipeLayouts = recipeLayoutsWithButtonsField;
            if (recipeLayouts == null) {
                recipeLayouts = declaredField(layoutsValue.getClass(), "recipeLayoutsWithButtons");
                recipeLayoutsWithButtonsField = recipeLayouts;
            }
            Object value = recipeLayouts.get(layoutsValue);
            return value instanceof List<?> list ? list : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            Ae2UtilityMod.LOGGER.warn("Failed to access JEI visible recipe layouts", e);
            return null;
        }
    }

    private static @Nullable IRecipeLayoutDrawable<?> getRecipeLayout(Object layoutWithButtons) {
        if (layoutWithButtons == null || reflectionFailed) {
            return null;
        }

        try {
            Method method = recipeLayoutMethod;
            if (method == null) {
                method = declaredMethod(layoutWithButtons.getClass(), "recipeLayout");
                recipeLayoutMethod = method;
            }
            Object value = method.invoke(layoutWithButtons);
            return value instanceof IRecipeLayoutDrawable<?> recipeLayout ? recipeLayout : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            Ae2UtilityMod.LOGGER.warn("Failed to access JEI recipe layout", e);
            return null;
        }
    }

    public static List<IRecipeLayoutDrawable<?>> createSelectedCategoryRecipeLayouts(Screen screen) {
        if (reflectionFailed || !isRecipesGui(screen)) {
            return List.of();
        }

        IJeiRuntime runtime = EncodePatternButtonState.getJeiRuntime();
        if (runtime == null) {
            return List.of();
        }

        try {
            Field logicRef = logicField;
            if (logicRef == null) {
                logicRef = declaredField(screen.getClass(), "logic");
                logicField = logicRef;
            }
            Object logic = logicRef.get(screen);
            if (logic == null) {
                return List.of();
            }

            Field stateRef = stateField;
            if (stateRef == null) {
                stateRef = declaredField(logic.getClass(), "state");
                stateField = stateRef;
            }
            Object state = stateRef.get(logic);
            if (state == null) {
                return List.of();
            }

            Object focusedRecipes = invokeFocusedRecipes(state);
            if (focusedRecipes == null) {
                return List.of();
            }

            Object category = invokeRecipeCategory(focusedRecipes);
            Object recipesValue = invokeRecipes(focusedRecipes);
            if (!(category instanceof IRecipeCategory<?> recipeCategory) || !(recipesValue instanceof List<?> recipes)) {
                return List.of();
            }

            IFocusGroup emptyFocus = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
            return createLayouts(runtime, recipeCategory, recipes, emptyFocus);
        } catch (ReflectiveOperationException | RuntimeException e) {
            reflectionFailed = true;
            Ae2UtilityMod.LOGGER.warn("Failed to access JEI selected recipe category", e);
            return List.of();
        }
    }

    private static @Nullable Object invokeFocusedRecipes(Object state)
            throws ReflectiveOperationException {
        Method method = focusedRecipesMethod;
        if (method == null) {
            method = state.getClass().getMethod("getFocusedRecipes");
            focusedRecipesMethod = method;
        }
        return method.invoke(state);
    }

    private static @Nullable Object invokeRecipes(Object focusedRecipes)
            throws ReflectiveOperationException {
        Method method = recipesMethod;
        if (method == null) {
            method = focusedRecipes.getClass().getMethod("getRecipes");
            recipesMethod = method;
        }
        return method.invoke(focusedRecipes);
    }

    private static @Nullable Object invokeRecipeCategory(Object focusedRecipes)
            throws ReflectiveOperationException {
        Method method = recipeCategoryMethod;
        if (method == null) {
            method = focusedRecipes.getClass().getMethod("getRecipeCategory");
            recipeCategoryMethod = method;
        }
        return method.invoke(focusedRecipes);
    }

    private static Field declaredField(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method declaredMethod(Class<?> type, String name) throws ReflectiveOperationException {
        Method method = type.getDeclaredMethod(name);
        method.setAccessible(true);
        return method;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<IRecipeLayoutDrawable<?>> createLayouts(IJeiRuntime runtime,
            IRecipeCategory<?> recipeCategory, List<?> recipes, IFocusGroup focusGroup) {
        List<IRecipeLayoutDrawable<?>> result = new ArrayList<>(recipes.size());
        IRecipeCategory rawCategory = recipeCategory;
        for (Object recipe : recipes) {
            Optional<IRecipeLayoutDrawable<?>> layout =
                    (Optional) runtime.getRecipeManager().createRecipeLayoutDrawable(rawCategory, recipe, focusGroup);
            layout.ifPresent(result::add);
        }
        return result;
    }
}
