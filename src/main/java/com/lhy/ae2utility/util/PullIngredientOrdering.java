package com.lhy.ae2utility.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

/**
 * 1.20.1：物品「特异性」按 NBT 标签判断（1.21 用数据组件 hash，此处用 hasTag 兜底）。
 */
public final class PullIngredientOrdering {

    private PullIngredientOrdering() {
    }

    public static List<ItemStack> preferSpecificComponentsFirst(List<ItemStack> alternatives) {
        if (alternatives == null || alternatives.isEmpty()) {
            return alternatives == null ? List.of() : List.copyOf(alternatives);
        }
        if (alternatives.size() == 1) {
            return List.copyOf(alternatives);
        }
        List<ItemStack> copy = new ArrayList<>(alternatives.size());
        copy.addAll(alternatives);
        copy.sort(ITEM_STACK_COMPARATOR);
        return copy;
    }

    public static int genericStackItemSpecificityRank(GenericStack stack) {
        if (stack == null || stack.what() == null) {
            return 0;
        }
        if (!(stack.what() instanceof AEItemKey itemKey)) {
            return 0;
        }
        return itemKeyComponentSpecificityRank(itemKey);
    }

    public static int itemKeyComponentSpecificityRank(AEItemKey key) {
        if (key == null) {
            return 0;
        }
        return componentSpecificityRank(key.toStack().copy());
    }

    public static int componentSpecificityRank(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return stack.hasTag() ? 1 : 0;
    }

    private static final Comparator<ItemStack> ITEM_STACK_COMPARATOR = Comparator
            .comparingInt(PullIngredientOrdering::componentSpecificityRank)
            .reversed()
            .thenComparingInt(ItemStack::getDamageValue)
            .thenComparing(stack -> stack.getItem().hashCode());
}
