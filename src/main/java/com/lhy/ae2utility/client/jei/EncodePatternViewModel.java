package com.lhy.ae2utility.client.jei;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.lhy.ae2utility.util.PatternEncodingPreview;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;

/** Prepared render data; containers cannot be mutated by render or ingredient-display hooks. */
public record EncodePatternViewModel(int backdropColor, List<IRecipeSlotView> inputSlots,
        Map<IRecipeSlotView, Boolean> craftableInputs,
        Map<IRecipeSlotView, ITypedIngredient<?>> chosenIngredients,
        @Nullable PatternEncodingPreview terminalPreview) {
    public static final EncodePatternViewModel EMPTY = new EncodePatternViewModel(0, List.of(), Map.of(), Map.of(), null);

    public EncodePatternViewModel {
        inputSlots = List.copyOf(inputSlots);
        craftableInputs = Collections.unmodifiableMap(new IdentityHashMap<>(craftableInputs));
        chosenIngredients = Collections.unmodifiableMap(new IdentityHashMap<>(chosenIngredients));
    }
}
