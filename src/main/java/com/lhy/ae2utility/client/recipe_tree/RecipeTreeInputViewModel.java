package com.lhy.ae2utility.client.recipe_tree;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;

import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.world.item.ItemStack;

public final class RecipeTreeInputViewModel {
    private final @Nullable RequestedIngredient requestedIngredient;
    private final List<DisplayOption> displayOptions;
    private final int amount;
    private final String amountText;
    private RecipeTreeNodeViewModel child;
    private int selectedAlternativeIndex;

    public RecipeTreeInputViewModel(@Nullable RequestedIngredient requestedIngredient, List<DisplayOption> displayOptions, int amount,
            String amountText) {
        this.requestedIngredient = requestedIngredient == null ? null : requestedIngredient.copy();
        this.displayOptions = List.copyOf(new ArrayList<>(displayOptions));
        this.amount = Math.max(1, amount);
        this.amountText = amountText == null ? "" : amountText;
    }

    public @Nullable RequestedIngredient requestedIngredient() {
        return requestedIngredient == null ? null : requestedIngredient.copy();
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
}
