package com.lhy.ae2utility.client.recipe_tree;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;

import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.world.item.ItemStack;

public final class RecipeTreeInputViewModel {
    private final @Nullable RequestedIngredient requestedIngredient;
    private final @Nullable String requestedIngredientSignature;
    private final List<DisplayOption> displayOptions;
    private final int amount;
    private final String amountText;
    private RecipeTreeNodeViewModel child;
    private int selectedAlternativeIndex;

    public RecipeTreeInputViewModel(@Nullable RequestedIngredient requestedIngredient, List<DisplayOption> displayOptions, int amount,
            String amountText) {
        this.requestedIngredient = requestedIngredient == null ? null : requestedIngredient.copy();
        this.requestedIngredientSignature = signatureOf(this.requestedIngredient);
        this.displayOptions = List.copyOf(new ArrayList<>(displayOptions));
        this.amount = Math.max(1, amount);
        this.amountText = amountText == null ? "" : amountText;
    }

    public @Nullable RequestedIngredient requestedIngredient() {
        return requestedIngredient == null ? null : requestedIngredient.copy();
    }

    public @Nullable RequestedIngredient requestedIngredientView() {
        return requestedIngredient;
    }

    public @Nullable String requestedIngredientSignature() {
        return requestedIngredientSignature;
    }

    public @Nullable RequestedIngredient selectedRequestedIngredient() {
        if (requestedIngredient == null) {
            return null;
        }
        if (displayOptions.size() <= 1 || requestedIngredient.alternatives().isEmpty()) {
            return requestedIngredient.copy();
        }
        List<ItemStack> orderedAlternatives = orderedAlternatives();
        return new RequestedIngredient(orderedAlternatives, requestedIngredient.count());
    }

    public ItemStack displayStack() {
        DisplayOption option = displayOption();
        if (option != null && !option.itemStack().isEmpty()) {
            return option.itemStack().copyWithCount(amount);
        }
        return ItemStack.EMPTY;
    }

    public @Nullable ITypedIngredient<?> displayIngredient() {
        DisplayOption option = displayOption();
        return option == null ? null : option.typedIngredient();
    }

    public String displayName() {
        DisplayOption option = displayOption();
        return option == null ? "" : option.label();
    }

    public int amount() {
        return amount;
    }

    public String amountText() {
        return amountText;
    }

    public List<DisplayOption> displayOptions() {
        return displayOptions;
    }

    public List<DisplayOption> orderedDisplayOptions() {
        if (displayOptions.size() <= 1) {
            return displayOptions;
        }
        int size = displayOptions.size();
        int start = Math.max(0, Math.min(selectedAlternativeIndex, size - 1));
        List<DisplayOption> ordered = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ordered.add(displayOptions.get((start + i) % size));
        }
        return List.copyOf(ordered);
    }

    public RecipeTreeNodeViewModel child() {
        return child;
    }

    public void setChild(RecipeTreeNodeViewModel child) {
        this.child = child;
    }

    public boolean hasAlternativeChoices() {
        return displayOptions.size() > 1;
    }

    public void cycleAlternative() {
        int size = displayOptions.size();
        if (size > 1) {
            selectedAlternativeIndex = (selectedAlternativeIndex + 1) % size;
        }
    }

    public void selectAlternative(int index) {
        int size = displayOptions.size();
        if (size > 0) {
            selectedAlternativeIndex = Math.max(0, Math.min(size - 1, index));
        }
    }

    public int selectedAlternativeIndex() {
        return selectedAlternativeIndex;
    }

    public List<ItemStack> orderedAlternatives() {
        if (requestedIngredient == null) {
            return List.of();
        }
        List<ItemStack> ordered = orderedAlternativesView();
        if (ordered.isEmpty()) {
            return ordered;
        }
        List<ItemStack> copied = new ArrayList<>(ordered.size());
        for (ItemStack stack : ordered) {
            copied.add(stack.copy());
        }
        return List.copyOf(copied);
    }

    public List<ItemStack> orderedAlternativesView() {
        if (requestedIngredient == null) {
            return List.of();
        }
        List<ItemStack> alternatives = requestedIngredient.alternatives();
        if (alternatives.size() <= 1) {
            return alternatives;
        }
        int size = alternatives.size();
        int start = Math.max(0, Math.min(selectedAlternativeIndex, size - 1));
        List<ItemStack> ordered = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ordered.add(alternatives.get((start + i) % size));
        }
        return List.copyOf(ordered);
    }

    private @Nullable DisplayOption displayOption() {
        if (displayOptions.isEmpty()) {
            return null;
        }
        int index = Math.max(0, Math.min(selectedAlternativeIndex, displayOptions.size() - 1));
        return displayOptions.get(index);
    }

    public record DisplayOption(@Nullable ITypedIngredient<?> typedIngredient, String label, ItemStack itemStack) {
        public DisplayOption {
            label = label == null ? "" : label;
            itemStack = itemStack == null ? ItemStack.EMPTY : itemStack.copy();
        }
    }

    private static @Nullable String signatureOf(@Nullable RequestedIngredient ingredient) {
        if (ingredient == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (ItemStack alternative : ingredient.alternatives()) {
            if (!alternative.isEmpty()) {
                parts.add("itemtype#" + alternative.getItem());
            }
        }
        parts.sort(String::compareTo);
        return "requested#" + String.join("|", parts);
    }
}
