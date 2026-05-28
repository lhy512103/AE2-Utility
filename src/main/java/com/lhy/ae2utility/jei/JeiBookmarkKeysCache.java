package com.lhy.ae2utility.jei;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import appeng.api.stacks.AEKey;

/**
 * JEI 收藏栏 AEKey 列表；同一客户端 tick 内最多反射采样一次。
 */
public final class JeiBookmarkKeysCache {
    private static long cacheTickGeneration = Long.MIN_VALUE;
    private static List<AEKey> cachedKeys = List.of();

    private JeiBookmarkKeysCache() {
    }

    public static List<AEKey> getBookmarkKeys() {
        long tickGeneration = JeiClientCacheContext.getTickGeneration();
        if (cacheTickGeneration == tickGeneration) {
            return cachedKeys;
        }

        cachedKeys = loadBookmarkKeys();
        cacheTickGeneration = tickGeneration;
        return cachedKeys;
    }

    public static long getBookmarkSignature() {
        long signature = 1L;
        for (AEKey key : getBookmarkKeys()) {
            signature = 31L * signature + (key == null ? 0L : key.hashCode());
        }
        return signature;
    }

    /** 收藏栏在外部被清空后调用，避免仍使用缓存的书签 AEKey。 */
    public static void invalidate() {
        cacheTickGeneration = Long.MIN_VALUE;
        cachedKeys = List.of();
    }

    private static List<AEKey> loadBookmarkKeys() {
        mezz.jei.api.runtime.IJeiRuntime runtime = Ae2UtilityJeiPlugin.getJeiRuntime();
        if (runtime == null) {
            return List.of();
        }

        mezz.jei.api.runtime.IBookmarkOverlay overlay = runtime.getBookmarkOverlay();
        if (overlay == null) {
            return List.of();
        }

        try {
            Field bookmarkListField = overlay.getClass().getDeclaredField("bookmarkList");
            bookmarkListField.setAccessible(true);
            Object bookmarkList = bookmarkListField.get(overlay);

            Method getElementsMethod = bookmarkList.getClass().getMethod("getElements");
            List<?> elements = (List<?>) getElementsMethod.invoke(bookmarkList);

            List<AEKey> bookmarks = new ArrayList<>(elements.size());
            for (Object element : elements) {
                Method getTypedIngredientMethod = element.getClass().getMethod("getTypedIngredient");
                mezz.jei.api.ingredients.ITypedIngredient<?> typedIngredient =
                        (mezz.jei.api.ingredients.ITypedIngredient<?>) getTypedIngredientMethod.invoke(element);
                if (typedIngredient == null) {
                    continue;
                }

                AEKey key = com.lhy.ae2utility.util.GenericIngredientUtil.toAEKey(typedIngredient.getIngredient());
                if (key != null) {
                    bookmarks.add(key);
                }
            }
            return List.copyOf(bookmarks);
        } catch (Throwable e) {
            return List.of();
        }
    }
}
