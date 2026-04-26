package com.lhy.ae2utility.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.util.prioritylist.IPartitionList;

/**
 * ME extraction that mirrors JEI / AE2 terminal {@code Ingredient} checks: try exact {@link AEItemKey}s from JEI
 * stacks first, then any stored stack matching a reconstructed {@link Ingredient}, then same item id as any
 * alternative (covers Occultism-style bindings when JEI only lists a prototype stack).
 */
public final class MeIngredientExtraction {

    private MeIngredientExtraction() {
    }

    @Nullable
    public static Ingredient ingredientFromItemStacks(List<ItemStack> alternativeStacks) {
        ItemStack[] stacks = alternativeStacks.stream()
                .filter(Objects::nonNull)
                .filter(s -> !s.isEmpty())
                .map(ItemStack::copy)
                .toArray(ItemStack[]::new);
        if (stacks.length == 0) {
            return null;
        }
        return Ingredient.of(stacks);
    }

    public static List<ItemStack> extractAlternatives(MEStorage storage, IEnergySource energy, IActionSource actionSource,
            @Nullable IPartitionList filter, List<ItemStack> alternativeStacks, int amount) {
        List<AEItemKey> preferredKeys = PullIngredientOrdering.preferSpecificItemKeysFirst(
                alternativeStacks.stream()
                        .map(AEItemKey::of)
                        .filter(Objects::nonNull)
                        .filter(k -> filter == null || filter.isListed(k))
                        .distinct()
                        .toList());
        Ingredient wide = ingredientFromItemStacks(alternativeStacks);
        List<ItemStack> orderedAlts = PullIngredientOrdering.preferSpecificComponentsFirst(alternativeStacks);

        Map<AEItemKey, Integer> extractedByKey = new LinkedHashMap<>();
        for (int i = 0; i < amount; i++) {
            AEItemKey used = tryExtractOneUnit(storage, energy, actionSource, filter, preferredKeys, wide, orderedAlts);
            if (used == null) {
                break;
            }
            extractedByKey.merge(used, 1, Integer::sum);
        }
        return extractedByKey.entrySet().stream()
                .map(e -> e.getKey().toStack(e.getValue()))
                .toList();
    }

    @Nullable
    private static AEItemKey tryExtractOneUnit(MEStorage storage, IEnergySource energy, IActionSource actionSource,
            @Nullable IPartitionList filter, List<AEItemKey> preferredKeys, @Nullable Ingredient wide,
            List<ItemStack> orderedAlts) {
        for (AEItemKey candidate : preferredKeys) {
            long extracted = StorageHelper.poweredExtraction(energy, storage, candidate, 1, actionSource);
            if (extracted > 0) {
                return candidate;
            }
        }
        if (wide != null) {
            for (var entry : storage.getAvailableStacks()) {
                if (entry.getLongValue() <= 0 || !(entry.getKey() instanceof AEItemKey itemKey)) {
                    continue;
                }
                if (filter != null && !filter.isListed(itemKey)) {
                    continue;
                }
                if (!itemKey.matches(wide)) {
                    continue;
                }
                long extracted = StorageHelper.poweredExtraction(energy, storage, itemKey, 1, actionSource);
                if (extracted > 0) {
                    return itemKey;
                }
            }
        }
        for (ItemStack alt : orderedAlts) {
            if (alt.isEmpty()) {
                continue;
            }
            var wantedItem = alt.getItem();
            for (var entry : storage.getAvailableStacks()) {
                if (entry.getLongValue() <= 0 || !(entry.getKey() instanceof AEItemKey itemKey)) {
                    continue;
                }
                if (filter != null && !filter.isListed(itemKey)) {
                    continue;
                }
                if (itemKey.getItem() != wantedItem) {
                    continue;
                }
                long extracted = StorageHelper.poweredExtraction(energy, storage, itemKey, 1, actionSource);
                if (extracted > 0) {
                    return itemKey;
                }
            }
        }
        return null;
    }

    /**
     * Reserves one item from a frequency map (max-transfer simulation), using the same tiered logic as
     * {@link #extractAlternatives}.
     *
     * @return true if one unit was reserved
     */
    public static boolean reserveOneUnit(Map<AEItemKey, Long> remaining, List<ItemStack> alternativeStacks,
            @Nullable Ingredient wideIngredient) {
        List<AEItemKey> preferredKeys = PullIngredientOrdering.preferSpecificItemKeysFirst(
                alternativeStacks.stream()
                        .map(AEItemKey::of)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList());
        List<ItemStack> orderedAlts = PullIngredientOrdering.preferSpecificComponentsFirst(alternativeStacks);

        for (AEItemKey candidate : preferredKeys) {
            long available = remaining.getOrDefault(candidate, 0L);
            if (available > 0) {
                remaining.put(candidate, available - 1);
                return true;
            }
        }
        if (wideIngredient != null) {
            AEItemKey pick = null;
            long pickAmount = 0;
            for (var e : remaining.entrySet()) {
                AEItemKey key = e.getKey();
                long avail = e.getValue();
                if (avail <= 0) {
                    continue;
                }
                if (!key.matches(wideIngredient)) {
                    continue;
                }
                if (avail > pickAmount) {
                    pickAmount = avail;
                    pick = key;
                }
            }
            if (pick != null) {
                remaining.put(pick, pickAmount - 1);
                return true;
            }
        }
        for (ItemStack alt : orderedAlts) {
            if (alt.isEmpty()) {
                continue;
            }
            var wantedItem = alt.getItem();
            AEItemKey pick = null;
            long pickAmount = 0;
            for (var e : remaining.entrySet()) {
                AEItemKey key = e.getKey();
                long avail = e.getValue();
                if (avail <= 0 || key.getItem() != wantedItem) {
                    continue;
                }
                if (avail > pickAmount) {
                    pickAmount = avail;
                    pick = key;
                }
            }
            if (pick != null) {
                remaining.put(pick, pickAmount - 1);
                return true;
            }
        }
        return false;
    }
}
