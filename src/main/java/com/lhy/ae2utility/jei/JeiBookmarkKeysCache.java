package com.lhy.ae2utility.jei;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import appeng.api.stacks.AEKey;

import com.lhy.ae2utility.client.Ae2UtilityClientConfig;
import com.lhy.ae2utility.util.GenericIngredientUtil;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.overlay.elements.IElement;

/**
 * JEI 书签 AEKey 列表；同一客户端 tick 内最多反射采样一次。
 */
public final class JeiBookmarkKeysCache {
    private static long cacheTickGeneration = Long.MIN_VALUE;
    private static List<AEKey> cachedKeys = List.of();
    private static long cachedSignature = 1L;
    private static boolean reflectionFailed;

    private JeiBookmarkKeysCache() {
    }

    public static List<AEKey> getBookmarkKeys() {
        if (!Ae2UtilityClientConfig.preferFavoritesForPatternEncoding()) {
            return List.of();
        }
        long tickGeneration = JeiClientCacheContext.getTickGeneration();
        if (cacheTickGeneration != tickGeneration) {
            cachedKeys = loadBookmarkKeys();
            long signature = 1L;
            for (AEKey key : cachedKeys) {
                signature = 31L * signature + key.hashCode();
            }
            cachedSignature = signature;
            cacheTickGeneration = tickGeneration;
        }
        return cachedKeys;
    }

    public static long getBookmarkSignature() {
        getBookmarkKeys();
        return Ae2UtilityClientConfig.preferFavoritesForPatternEncoding() ? cachedSignature : 1L;
    }

    static BookmarkList bookmarkList(IBookmarkOverlay overlay) throws ReflectiveOperationException {
        Field field = overlay.getClass().getDeclaredField("bookmarkList");
        field.setAccessible(true);
        return (BookmarkList) field.get(overlay);
    }

    private static List<AEKey> loadBookmarkKeys() {
        if (reflectionFailed) {
            return List.of();
        }
        IJeiRuntime runtime = EncodePatternButtonState.getJeiRuntime();
        if (runtime == null) {
            return List.of();
        }
        try {
            List<IElement<?>> elements = bookmarkList(runtime.getBookmarkOverlay()).getElements();
            List<AEKey> keys = new ArrayList<>(elements.size());
            for (IElement<?> element : elements) {
                ITypedIngredient<?> typed = element.getTypedIngredient();
                AEKey key = typed == null ? null : GenericIngredientUtil.toAEKey(typed.getIngredient());
                if (key != null) {
                    keys.add(key);
                }
            }
            return List.copyOf(keys);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            reflectionFailed = true;
            return List.of();
        }
    }
}
