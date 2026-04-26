package com.lhy.ae2utility.network;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
    private RecipeTransferPacketHelper() {
    }

    public static List<AEKey> getBookmarkKeys() {
        mezz.jei.api.runtime.IJeiRuntime runtime = com.lhy.ae2utility.jei.Ae2UtilityJeiPlugin.getJeiRuntime();
        if (runtime == null) return List.of();
        
        mezz.jei.api.runtime.IBookmarkOverlay overlay = runtime.getBookmarkOverlay();
        if (overlay == null) return List.of();
        
        try {
            java.lang.reflect.Field bookmarkListField = overlay.getClass().getDeclaredField("bookmarkList");
            bookmarkListField.setAccessible(true);
            Object bookmarkList = bookmarkListField.get(overlay);
            
            java.lang.reflect.Method getElementsMethod = bookmarkList.getClass().getMethod("getElements");
            List<?> elements = (List<?>) getElementsMethod.invoke(bookmarkList);
            
            List<AEKey> bookmarks = new ArrayList<>();
            for (Object element : elements) {
                java.lang.reflect.Method getTypedIngredientMethod = element.getClass().getMethod("getTypedIngredient");
                mezz.jei.api.ingredients.ITypedIngredient<?> typedIngredient = (mezz.jei.api.ingredients.ITypedIngredient<?>) getTypedIngredientMethod.invoke(element);
                if (typedIngredient != null) {
                    AEKey key = GenericIngredientUtil.toAEKey(typedIngredient.getIngredient());
                    if (key != null) {
                        bookmarks.add(key);
                    }
                }
            }
            return bookmarks;
        } catch (Throwable e) {
            return List.of();
        }
    }

    public static List<List<GenericStack>> getGenericStacks(IRecipeSlotsView slotsView, RecipeIngredientRole role) {
        List<List<GenericStack>> slots = new ArrayList<>();
        List<AEKey> bookmarkedKeys = getBookmarkKeys();
        
        for (IRecipeSlotView slotView : slotsView.getSlotViews(role)) {
            List<GenericStack> alternatives = new ArrayList<>();
            
            // Determine the requested count for this slot
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
                for (ITypedIngredient<?> typed : slotView.getAllIngredients().toList()) {
                    long chemicalAmount = GenericIngredientUtil.tryGetMekanismChemicalAmount(typed.getIngredient());
                    if (chemicalAmount > 1) {
                        count = chemicalAmount;
                        break;
                    }
                }
            }
            
            // 1. Try to find a match in bookmarks first
            GenericStack bookmarkedStack = null;
            for (AEKey bookmarkedKey : bookmarkedKeys) {
                if (bookmarkedStack != null) break;
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
                slots.add(alternatives);
                continue;
            }
            
            // 2. If no bookmark, collect all alternatives in static order
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
            
            if (alternatives.isEmpty()) {
                slots.add(null);
            } else {
                slots.add(alternatives);
            }
        }
        return slots;
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
}
