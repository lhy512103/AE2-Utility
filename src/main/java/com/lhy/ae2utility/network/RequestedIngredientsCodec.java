package com.lhy.ae2utility.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/** Common wire format and request identity; must not depend on recipe viewers. */
public final class RequestedIngredientsCodec {
    private RequestedIngredientsCodec() {
    }

    public static List<RequestedIngredient> readRequestedIngredients(FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        boolean exceededLimits = size < 0 || size > NetworkValidation.MAX_REQUESTED_INGREDIENTS;
        long totalAlternatives = 0;
        List<RequestedIngredient> ingredients = new ArrayList<>(Math.min(Math.max(size, 0),
                NetworkValidation.MAX_REQUESTED_INGREDIENTS));
        for (int i = 0; i < size; i++) {
            int alternativesSize = buffer.readVarInt();
            if (alternativesSize < 0) {
                return List.of();
            }
            totalAlternatives = Math.min(Integer.MAX_VALUE, totalAlternatives + alternativesSize);
            boolean keepIngredient = !exceededLimits
                    && alternativesSize <= NetworkValidation.MAX_ALTERNATIVES_PER_INGREDIENT
                    && totalAlternatives <= NetworkValidation.MAX_TOTAL_INGREDIENT_ALTERNATIVES;
            if (!keepIngredient) {
                exceededLimits = true;
            }

            List<ItemStack> alternatives = keepIngredient ? new ArrayList<>(alternativesSize) : List.of();
            for (int j = 0; j < alternativesSize; j++) {
                ItemStack alternative = com.lhy.ae2utility.network.codec.ForgeCodecs.ITEM_STACK.decode(buffer);
                if (keepIngredient) {
                    alternatives.add(alternative);
                }
            }
            int count = buffer.readVarInt();
            if (keepIngredient) {
                ingredients.add(new RequestedIngredient(alternatives, count));
            }
        }
        return exceededLimits ? List.of() : ingredients;
    }

    public static boolean canEncodeRequestedIngredients(List<RequestedIngredient> ingredients) {
        if (ingredients.size() > NetworkValidation.MAX_REQUESTED_INGREDIENTS) {
            return false;
        }
        long totalAlternatives = 0;
        for (RequestedIngredient ingredient : ingredients) {
            int alternativesSize = ingredient.alternatives().size();
            if (alternativesSize > NetworkValidation.MAX_ALTERNATIVES_PER_INGREDIENT) {
                return false;
            }
            totalAlternatives = Math.min(Integer.MAX_VALUE, totalAlternatives + alternativesSize);
            if (totalAlternatives > NetworkValidation.MAX_TOTAL_INGREDIENT_ALTERNATIVES) {
                return false;
            }
        }
        return true;
    }

    public static void writeRequestedIngredients(FriendlyByteBuf buffer, List<RequestedIngredient> ingredients) {
        if (!canEncodeRequestedIngredients(ingredients)) {
            buffer.writeVarInt(0);
            return;
        }
        buffer.writeVarInt(ingredients.size());
        for (RequestedIngredient ingredient : ingredients) {
            buffer.writeVarInt(ingredient.alternatives().size());
            for (ItemStack alternative : ingredient.alternatives()) {
                com.lhy.ae2utility.network.codec.ForgeCodecs.ITEM_STACK.encode(buffer, alternative);
            }
            buffer.writeVarInt(ingredient.count());
        }
    }

    public static String requestSignature(List<RequestedIngredient> ingredients) {
        return ingredients.stream()
                .map(RequestedIngredientsCodec::normalizeRequestedIngredient)
                .sorted(Comparator.comparing(RequestedIngredientsCodec::requestedIngredientKey))
                .map(RequestedIngredientsCodec::requestedIngredientKey)
                .reduce((left, right) -> left + "||" + right)
                .orElse("");
    }

    private static RequestedIngredient normalizeRequestedIngredient(RequestedIngredient ingredient) {
        List<ItemStack> normalizedAlternatives = ingredient.alternatives().stream()
                .map(ItemStack::copy)
                .sorted(Comparator.comparing(RequestedIngredientsCodec::stackKey))
                .toList();
        return new RequestedIngredient(normalizedAlternatives, ingredient.count());
    }

    private static String requestedIngredientKey(RequestedIngredient ingredient) {
        String alternativesKey = ingredient.alternatives().stream()
                .map(RequestedIngredientsCodec::stackKey)
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
        return ingredient.count() + "#" + alternativesKey;
    }

    private static String stackKey(ItemStack stack) {
        return com.lhy.ae2utility.util.ItemIds.stackHash(stack) + "#" + stack.getCount() + "#" + stack.getItem();
    }

}
