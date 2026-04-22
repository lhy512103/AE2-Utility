package com.lhy.ae2utility.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;

import appeng.api.stacks.GenericStack;
import appeng.api.stacks.GenericStack;

import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;

public final class RecipeTransferPacketHelper {
    private RecipeTransferPacketHelper() {
    }

    public static List<appeng.api.stacks.AEKey> getBookmarkKeys() {
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
            
            List<appeng.api.stacks.AEKey> bookmarks = new ArrayList<>();
            for (Object element : elements) {
                java.lang.reflect.Method getTypedIngredientMethod = element.getClass().getMethod("getTypedIngredient");
                mezz.jei.api.ingredients.ITypedIngredient<?> typedIngredient = (mezz.jei.api.ingredients.ITypedIngredient<?>) getTypedIngredientMethod.invoke(element);
                if (typedIngredient != null) {
                    Object ingredient = typedIngredient.getIngredient();
                    appeng.api.stacks.AEKey key = getAEKey(ingredient);
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

    private static appeng.api.stacks.AEKey getAEKey(Object ingredient) {
        if (ingredient instanceof ItemStack is && !is.isEmpty()) {
            return appeng.api.stacks.AEItemKey.of(is);
        } else if (ingredient instanceof FluidStack fs && !fs.isEmpty()) {
            return appeng.api.stacks.AEFluidKey.of(fs);
        }
        return null;
    }

    public static List<List<GenericStack>> getGenericStacks(IRecipeSlotsView slotsView, RecipeIngredientRole role) {
        List<List<GenericStack>> slots = new ArrayList<>();
        List<appeng.api.stacks.AEKey> bookmarkedKeys = getBookmarkKeys();
        
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
            
            // 1. Try to find a match in bookmarks first
            GenericStack bookmarkedStack = null;
            for (appeng.api.stacks.AEKey bookmarkedKey : bookmarkedKeys) {
                if (bookmarkedStack != null) break;
                for (ITypedIngredient<?> typed : slotView.getAllIngredients().toList()) {
                    Object ing = typed.getIngredient();
                    appeng.api.stacks.AEKey ingKey = getAEKey(ing);
                    if (ingKey != null && ingKey.equals(bookmarkedKey)) {
                        if (ing instanceof ItemStack is) bookmarkedStack = new GenericStack(ingKey, count);
                        else if (ing instanceof FluidStack fs) bookmarkedStack = new GenericStack(ingKey, count);
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
                appeng.api.stacks.AEKey ingKey = getAEKey(ing);
                if (ingKey != null) {
                    boolean alreadyAdded = false;
                    for (GenericStack existing : alternatives) {
                        if (existing.what().equals(ingKey)) {
                            alreadyAdded = true;
                            break;
                        }
                    }
                    if (!alreadyAdded) {
                        alternatives.add(new GenericStack(ingKey, count));
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

    public static List<RequestedIngredient> getRequestedIngredients(IRecipeSlotsView slotsView, RecipeIngredientRole role) {
        List<RequestedIngredient> ingredients = new ArrayList<>();
        for (IRecipeSlotView slotView : slotsView.getSlotViews(role)) {
            List<ItemStack> itemStacks = slotView.getIngredients(mezz.jei.api.constants.VanillaTypes.ITEM_STACK).toList();
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
}
