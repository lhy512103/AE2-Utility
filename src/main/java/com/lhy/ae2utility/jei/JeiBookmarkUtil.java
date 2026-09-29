package com.lhy.ae2utility.jei;

import java.util.List;

import appeng.api.stacks.GenericStack;

import com.lhy.ae2utility.network.RecipeTransferPacketHelper;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IngredientBookmark;

public final class JeiBookmarkUtil {
    private JeiBookmarkUtil() {
    }

    /**
     * 把 ME 网络中没有自动合成样板的输入槽加入 JEI 书签；多候选槽取静态顺序第一项，已在书签中的跳过。
     *
     * @return 新加入书签的数量
     */
    public static int bookmarkMissingCraftingInputs(IRecipeSlotsView slotsView) {
        IJeiRuntime runtime = EncodePatternButtonState.getJeiRuntime();
        if (runtime == null) {
            return 0;
        }
        try {
            BookmarkList bookmarkList = JeiBookmarkKeysCache.bookmarkList(runtime.getBookmarkOverlay());
            int added = 0;
            for (IRecipeSlotView slotView : slotsView.getSlotViews(RecipeIngredientRole.INPUT)) {
                if (slotView.isEmpty() || anyAlternativeCraftable(slotView)) {
                    continue;
                }
                List<ITypedIngredient<?>> staticOrder = slotView.getAllIngredients().toList();
                if (staticOrder.isEmpty()) {
                    continue;
                }
                if (bookmarkList.add(IngredientBookmark.create(staticOrder.get(0), runtime.getIngredientManager()))) {
                    added++;
                }
            }
            return added;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return 0;
        }
    }

    private static boolean anyAlternativeCraftable(IRecipeSlotView slotView) {
        for (GenericStack alt : RecipeTransferPacketHelper.collectEncodeAlternativesForInputSlot(slotView)) {
            if (alt.what() != null && CraftableStateCache.isCraftable(alt.what())) {
                return true;
            }
        }
        return false;
    }
}
