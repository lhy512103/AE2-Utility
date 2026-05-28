package com.lhy.ae2utility.network;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jetbrains.annotations.Nullable;

import com.lhy.ae2utility.jei.JeiBookmarkKeysCache;
import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;
import com.lhy.ae2utility.util.GenericIngredientUtil;
import com.lhy.ae2utility.util.PullIngredientOrdering;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.network.chat.Component;

public final class RecipeTransferPacketHelper {
    private static final ConcurrentMap<StochasticOutputCacheKey, Boolean> STOCHASTIC_OUTPUT_CACHE = new ConcurrentHashMap<>();

    private RecipeTransferPacketHelper() {
    }

    public static List<AEKey> getBookmarkKeys() {
        return JeiBookmarkKeysCache.getBookmarkKeys();
    }

    public static List<List<GenericStack>> getGenericStacks(IRecipeSlotsView slotsView, RecipeIngredientRole role) {
        List<List<GenericStack>> slots = new ArrayList<>();
        List<AEKey> bookmarkedKeys = getBookmarkKeys();

        for (IRecipeSlotView slotView : slotsView.getSlotViews(role)) {
            List<GenericStack> alternatives = collectEncodeAlternativesForInputSlot(slotView, bookmarkedKeys);
            if (alternatives.isEmpty()) {
                slots.add(isEffectivelyEmptySlot(slotView) ? List.of() : null);
            } else {
                slots.add(alternatives);
            }
        }
        return slots;
    }

    /**
     * 构建与编码发包一致的单个输入槽备选列表（书签命中则仅此一项），供客户端预览与服务端共用选股数据源。
     */
    public static List<GenericStack> collectEncodeAlternativesForInputSlot(IRecipeSlotView slotView) {
        return collectEncodeAlternativesForInputSlot(slotView, getBookmarkKeys());
    }

    /**
     * 在槽内原料顺序下找到与选定 {@link GenericStack} 键一致的首个展示原料（与发包侧去重顺序一致）。
     */
    public static Optional<ITypedIngredient<?>> findTypedIngredientMatchingChosen(IRecipeSlotView slotView, GenericStack chosen) {
        if (chosen == null || chosen.what() == null) {
            return Optional.empty();
        }
        for (ITypedIngredient<?> typed : slotView.getAllIngredients().toList()) {
            Object ing = typed.getIngredient();
            AEKey ingKey = GenericIngredientUtil.toAEKey(ing);
            if (ingKey != null && ingKey.equals(chosen.what())) {
                return Optional.of(typed);
            }
        }
        return Optional.empty();
    }

    /**
     * 用于 JEI 样板高亮/UI：每个输入槽<strong>只取一条</strong>（书签命中时与 {@link #collectEncodeAlternativesForInputSlot} 一致；
     * 否则取 {@link IRecipeSlotView#getDisplayedIngredient()}，无则取 {@link IRecipeSlotView#getAllIngredients()} 的首项），
     * 避免对整组 tag 替补逐键 {@link com.lhy.ae2utility.jei.CraftableStateCache#isCraftable}。
     */
    public static @Nullable GenericStack genericStackForCraftableHighlightInputSlot(IRecipeSlotView slotView) {
        List<AEKey> bookmarkedKeys = getBookmarkKeys();
        long count = resolveEncodeSlotDisplayedCount(slotView);
        List<ITypedIngredient<?>> allIngredients = slotView.getAllIngredients().toList();

        return genericStackForCraftableHighlightInputSlot(slotView, allIngredients, bookmarkedKeys, count);
    }

    public static @Nullable GenericStack genericStackForCraftableHighlightInputSlot(IRecipeSlotView slotView,
            List<ITypedIngredient<?>> allIngredients, List<AEKey> bookmarkedKeys, long count) {
        for (AEKey bookmarkedKey : bookmarkedKeys) {
            for (ITypedIngredient<?> typed : allIngredients) {
                Object ing = typed.getIngredient();
                AEKey ingKey = GenericIngredientUtil.toAEKey(ing);
                if (ingKey != null && ingKey.equals(bookmarkedKey)) {
                    return new GenericStack(ingKey, GenericIngredientUtil.resolveAmount(ing, count));
                }
            }
        }

        ITypedIngredient<?> typed = slotView.getDisplayedIngredient().orElseGet(() -> allIngredients.isEmpty() ? null : allIngredients.getFirst());
        if (typed == null) {
            return null;
        }
        return GenericIngredientUtil.toGenericStack(typed.getIngredient(), count);
    }

    public static long resolveEncodeSlotDisplayedCount(IRecipeSlotView slotView, List<ITypedIngredient<?>> allIngredients) {
        long count = 1;
        List<ItemStack> itemStacks = slotView.getIngredients(mezz.jei.api.constants.VanillaTypes.ITEM_STACK).toList();
        for (ItemStack stack : itemStacks) {
            if (!stack.isEmpty() && stack.getCount() > 1) {
                count = stack.getCount();
                break;
            }
        }
        if (count == 1) {
            List<FluidStack> fluidStacks = slotView.getIngredients(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK).toList();
            for (FluidStack stack : fluidStacks) {
                if (!stack.isEmpty() && stack.getAmount() > 1) {
                    count = stack.getAmount();
                    break;
                }
            }
        }
        if (count == 1) {
            for (ITypedIngredient<?> typed : allIngredients) {
                long chemicalAmount = GenericIngredientUtil.tryGetMekanismChemicalAmount(typed.getIngredient());
                if (chemicalAmount > 1) {
                    count = chemicalAmount;
                    break;
                }
            }
        }
        return count;
    }

    private static List<GenericStack> collectEncodeAlternativesForInputSlot(IRecipeSlotView slotView, List<AEKey> bookmarkedKeys) {
        List<GenericStack> alternatives = new ArrayList<>();

        long count = resolveEncodeSlotDisplayedCount(slotView);

        GenericStack bookmarkedStack = null;
        for (AEKey bookmarkedKey : bookmarkedKeys) {
            if (bookmarkedStack != null) {
                break;
            }
            for (ITypedIngredient<?> typed : slotView.getAllIngredients().toList()) {
                Object ing = typed.getIngredient();
                AEKey ingKey = GenericIngredientUtil.toAEKey(ing);
                if (ingKey != null && ingKey.equals(bookmarkedKey)) {
                    bookmarkedStack = new GenericStack(ingKey, GenericIngredientUtil.resolveAmount(ing, count));
                    break;
                }
            }
        }

        if (bookmarkedStack != null) {
            alternatives.add(bookmarkedStack);
            return alternatives;
        }

        for (ITypedIngredient<?> typed : slotView.getAllIngredients().toList()) {
            Object ing = typed.getIngredient();
            AEKey ingKey = GenericIngredientUtil.toAEKey(ing);
            if (ingKey != null) {
                boolean alreadyAdded = false;
                for (GenericStack existing : alternatives) {
                    if (existing.what().equals(ingKey)) {
                        alreadyAdded = true;
                        break;
                    }
                }
                if (!alreadyAdded) {
                    alternatives.add(new GenericStack(ingKey, GenericIngredientUtil.resolveAmount(ing, count)));
                }
            }
        }

        return alternatives;
    }

    private static boolean isEffectivelyEmptySlot(IRecipeSlotView slotView) {
        if (slotView.isEmpty()) {
            return true;
        }
        for (ITypedIngredient<?> typed : slotView.getAllIngredients().toList()) {
            Object ingredient = typed.getIngredient();
            if (ingredient instanceof ItemStack stack && stack.isEmpty()) {
                continue;
            }
            if (ingredient instanceof FluidStack fluid && fluid.isEmpty()) {
                continue;
            }
            if (GenericIngredientUtil.tryGetMekanismChemicalAmount(ingredient) <= 0) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static long resolveEncodeSlotDisplayedCount(IRecipeSlotView slotView) {
        return resolveEncodeSlotDisplayedCount(slotView, slotView.getAllIngredients().toList());
    }

    public static List<GenericStack> getEncodingOutputs(Object recipe, IRecipeSlotsView slotsView) {
        List<GenericStack> outputs = new ArrayList<>();
        int outputIndex = 0;
        for (IRecipeSlotView slotView : slotsView.getSlotViews(RecipeIngredientRole.OUTPUT)) {
            if (shouldSkipOutputSlotForEncoding(recipe, outputIndex, slotView)) {
                outputIndex++;
                continue;
            }
            if (isProbablyStochasticOutputSlot(slotView)) {
                outputIndex++;
                continue;
            }
            List<List<GenericStack>> singleSlot = getGenericStacks(new SingleRoleSlotsView(slotView), RecipeIngredientRole.OUTPUT);
            if (!singleSlot.isEmpty()) {
                List<GenericStack> list = singleSlot.getFirst();
                outputs.add(list == null || list.isEmpty() ? null : list.getFirst());
            }
            outputIndex++;
        }
        return outputs;
    }

    public static List<GenericStack> getEncodingOutputs(IRecipeSlotsView slotsView) {
        return getEncodingOutputs(null, slotsView);
    }

    public static List<RequestedIngredient> getRequestedIngredients(IRecipeSlotsView slotsView, RecipeIngredientRole role) {
        List<RequestedIngredient> ingredients = new ArrayList<>();
        for (IRecipeSlotView slotView : slotsView.getSlotViews(role)) {
            List<ItemStack> itemStacks = PullIngredientOrdering.preferSpecificComponentsFirst(
                    slotView.getIngredients(mezz.jei.api.constants.VanillaTypes.ITEM_STACK).toList());
            if (!itemStacks.isEmpty()) {
                // Determine the requested count, JEI slot view might have a count if there's a specific amount required.
                // Usually it's 1 unless the ingredient has a different count (e.g., custom sizes).
                int count = 1;
                for (ItemStack stack : itemStacks) {
                    if (!stack.isEmpty() && stack.getCount() > 1) {
                        count = stack.getCount();
                        break;
                    }
                }
                ingredients.add(new RequestedIngredient(itemStacks, count));
            }
        }
        return ingredients;
    }

    public static List<RequestedIngredient> readRequestedIngredients(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<RequestedIngredient> ingredients = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int alternativesSize = buffer.readVarInt();
            List<ItemStack> alternatives = new ArrayList<>(alternativesSize);
            for (int j = 0; j < alternativesSize; j++) {
                alternatives.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
            }
            ingredients.add(new RequestedIngredient(alternatives, buffer.readVarInt()));
        }
        return ingredients;
    }

    public static void writeRequestedIngredients(RegistryFriendlyByteBuf buffer, List<RequestedIngredient> ingredients) {
        buffer.writeVarInt(ingredients.size());
        for (RequestedIngredient ingredient : ingredients) {
            buffer.writeVarInt(ingredient.alternatives().size());
            for (ItemStack alternative : ingredient.alternatives()) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, alternative);
            }
            buffer.writeVarInt(ingredient.count());
        }
    }

    public static String requestSignature(List<RequestedIngredient> ingredients) {
        return ingredients.stream()
                .map(RecipeTransferPacketHelper::normalizeRequestedIngredient)
                .sorted(Comparator.comparing(RecipeTransferPacketHelper::requestedIngredientKey))
                .map(RecipeTransferPacketHelper::requestedIngredientKey)
                .reduce((left, right) -> left + "||" + right)
                .orElse("");
    }

    private static RequestedIngredient normalizeRequestedIngredient(RequestedIngredient ingredient) {
        List<ItemStack> normalizedAlternatives = ingredient.alternatives().stream()
                .map(ItemStack::copy)
                .sorted(Comparator.comparing(RecipeTransferPacketHelper::stackKey))
                .toList();
        return new RequestedIngredient(normalizedAlternatives, ingredient.count());
    }

    private static String requestedIngredientKey(RequestedIngredient ingredient) {
        String alternativesKey = ingredient.alternatives().stream()
                .map(RecipeTransferPacketHelper::stackKey)
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
        return ingredient.count() + "#" + alternativesKey;
    }

    private static String stackKey(ItemStack stack) {
        return ItemStack.hashItemAndComponents(stack) + "#" + stack.getCount() + "#" + stack.getItem();
    }

    private static boolean isProbablyStochasticOutputSlot(IRecipeSlotView slotView) {
        StochasticOutputCacheKey cacheKey = buildStochasticOutputCacheKey(slotView);
        if (cacheKey != null) {
            return STOCHASTIC_OUTPUT_CACHE.computeIfAbsent(cacheKey, ignored -> detectStochasticOutputSlot(slotView));
        }
        return detectStochasticOutputSlot(slotView);
    }

    private static boolean detectStochasticOutputSlot(IRecipeSlotView slotView) {
        try {
            Field tooltipCallbacksField = slotView.getClass().getDeclaredField("tooltipCallbacks");
            tooltipCallbacksField.setAccessible(true);
            List<?> callbacks = (List<?>) tooltipCallbacksField.get(slotView);
            if (callbacks == null || callbacks.isEmpty()) {
                return false;
            }

            Class<?> tooltipClass = Class.forName("mezz.jei.common.gui.JeiTooltip");
            Object tooltip = tooltipClass.getConstructor().newInstance();
            if (!(tooltip instanceof ITooltipBuilder tooltipBuilder)) {
                return false;
            }

            for (Object callback : callbacks) {
                if (callback == null) {
                    continue;
                }
                Method onRichTooltip = callback.getClass().getMethod("onRichTooltip", IRecipeSlotView.class, ITooltipBuilder.class);
                onRichTooltip.invoke(callback, slotView, tooltipBuilder);
            }

            @SuppressWarnings("deprecation")
            List<Component> lines = tooltipBuilder.toLegacyToComponents();
            for (Component line : lines) {
                String text = line.getString();
                String lower = text.toLowerCase(Locale.ROOT);
                if (text.contains("%") || lower.contains("chance") || lower.contains("probab")
                        || text.contains("概率") || text.contains("几率")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static @Nullable StochasticOutputCacheKey buildStochasticOutputCacheKey(IRecipeSlotView slotView) {
        ITypedIngredient<?> displayed =
                slotView.getDisplayedIngredient().orElseGet(() -> slotView.getAllIngredients().findFirst().orElse(null));
        if (displayed == null) {
            return null;
        }

        AEKey key = GenericIngredientUtil.toAEKey(displayed.getIngredient());
        if (key == null) {
            return null;
        }

        return new StochasticOutputCacheKey(slotView.getClass().getName(), key);
    }

    private static boolean shouldSkipOutputSlotForEncoding(Object recipe, int outputIndex, IRecipeSlotView slotView) {
        if (isMekanismSawmillChanceOutput(recipe, outputIndex)) {
            return true;
        }
        return false;
    }

    private static boolean isMekanismSawmillChanceOutput(Object recipe, int outputIndex) {
        if (recipe == null || outputIndex <= 0) {
            return false;
        }
        Object rawRecipe = unwrapRecipeHolder(recipe);
        if (rawRecipe == null || !rawRecipe.getClass().getName().equals("mekanism.api.recipes.basic.BasicSawmillRecipe")
                && !rawRecipe.getClass().getName().equals("mekanism.api.recipes.SawmillRecipe")) {
            // Support subclasses too.
            boolean sawmillType = false;
            for (Class<?> type = rawRecipe.getClass(); type != null; type = type.getSuperclass()) {
                if ("mekanism.api.recipes.SawmillRecipe".equals(type.getName())) {
                    sawmillType = true;
                    break;
                }
            }
            if (!sawmillType) {
                return false;
            }
        }
        try {
            Method getSecondaryChance = rawRecipe.getClass().getMethod("getSecondaryChance");
            Object chanceValue = getSecondaryChance.invoke(rawRecipe);
            if (chanceValue instanceof Number number) {
                double chance = number.doubleValue();
                return chance > 0 && chance < 1;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Object unwrapRecipeHolder(Object recipe) {
        if (recipe == null) {
            return null;
        }
        try {
            Method value = recipe.getClass().getMethod("value");
            return value.invoke(recipe);
        } catch (Throwable ignored) {
            return recipe;
        }
    }

    private record SingleRoleSlotsView(IRecipeSlotView slotView) implements IRecipeSlotsView {
        @Override
        public List<IRecipeSlotView> getSlotViews() {
            return List.of(slotView);
        }

        @Override
        public List<IRecipeSlotView> getSlotViews(RecipeIngredientRole role) {
            return slotView.getRole() == role ? List.of(slotView) : List.of();
        }
    }

    private record StochasticOutputCacheKey(String slotViewClass, AEKey displayedKey) {
    }
}
