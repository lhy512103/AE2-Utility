package com.lhy.ae2utility.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import appeng.menu.me.common.MEStorageMenu;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;

/**
 * 与 AE2 终端 JEI 拉配方相同的本地预览：背包占位 + {@link MEStorageMenu#hasIngredient} + 可合成替补。
 */
public final class TerminalJeRecipeTransferPreview {
    private static final byte MISSING = 1;
    private static final byte CRAFTABLE = 2;

    private static final long REFRESH_INTERVAL_TICKS = 10L;

    private static final Map<IRecipeSlotsView, Cached> CACHE = new WeakHashMap<>();

    private TerminalJeRecipeTransferPreview() {}

    private record Cached(byte[] flags, boolean anyResolved, long craftVersion, int invHash, long computedAtTick) {}

    public record PreviewSlots(
            List<IRecipeSlotView> missingSlots,
            List<IRecipeSlotView> craftableSlots,
            boolean anyResolved
    ) {
        public boolean anyCraftable() {
            return !craftableSlots.isEmpty();
        }
    }

    public static PreviewSlots compute(MEStorageMenu container, IRecipeSlotsView recipeSlots) {
        List<IRecipeSlotView> inputs = collectInputSlots(recipeSlots);

        long craftVersion = CraftableStateCache.cacheVersion();
        int invHash = playerInventoryHash(container);
        long tick = JeiClientCacheContext.getTickGeneration();

        Cached cached = CACHE.get(recipeSlots);
        if (cached != null
                && cached.flags.length == inputs.size()
                && cached.craftVersion == craftVersion
                && cached.invHash == invHash
                && tick - cached.computedAtTick < REFRESH_INTERVAL_TICKS) {
            return assemble(inputs, cached.flags, cached.anyResolved);
        }

        byte[] flags = new byte[inputs.size()];
        boolean anyResolved = computeFlags(container, inputs, flags);
        CACHE.put(recipeSlots, new Cached(flags, anyResolved, craftVersion, invHash, tick));
        return assemble(inputs, flags, anyResolved);
    }

    private static boolean computeFlags(MEStorageMenu container, List<IRecipeSlotView> inputs, byte[] flags) {
        var reservedTerminalAmounts = new it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<Object>();
        var playerItems = container.getPlayerInventory().items;
        var reservedPlayerItems = new int[playerItems.size()];
        boolean anyResolved = false;

        for (int idx = 0; idx < inputs.size(); idx++) {
            IRecipeSlotView slotView = inputs.get(idx);
            var ingredient = toIngredient(slotView);
            if (ingredient.isEmpty()) {
                continue;
            }

            int requiredCount = Math.max(getDisplayedStack(slotView).getCount(), 1);

            boolean missing = false;
            boolean craftable = false;
            for (int i = 0; i < requiredCount; i++) {
                boolean found = false;
                for (int slot = 0; slot < playerItems.size(); slot++) {
                    if (container.isPlayerInventorySlotLocked(slot)) {
                        continue;
                    }

                    var stack = playerItems.get(slot);
                    if (stack.getCount() - reservedPlayerItems[slot] > 0 && ingredient.test(stack)) {
                        reservedPlayerItems[slot]++;
                        found = true;
                        anyResolved = true;
                        break;
                    }
                }

                if (!found && container.hasIngredient(ingredient, reservedTerminalAmounts)) {
                    reservedTerminalAmounts.merge(ingredient, 1, Integer::sum);
                    found = true;
                    anyResolved = true;
                }

                if (!found && hasCraftableAlternative(container, ingredient)) {
                    craftable = true;
                    found = true;
                    anyResolved = true;
                }

                if (!found) {
                    missing = true;
                }
            }

            byte f = 0;
            if (missing) {
                f |= MISSING;
            }
            if (craftable) {
                f |= CRAFTABLE;
            }
            flags[idx] = f;
        }

        return anyResolved;
    }

    private static PreviewSlots assemble(List<IRecipeSlotView> inputs, byte[] flags, boolean anyResolved) {
        List<IRecipeSlotView> missingSlots = new ArrayList<>();
        List<IRecipeSlotView> craftableSlots = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            byte f = flags[i];
            if ((f & MISSING) != 0) {
                missingSlots.add(inputs.get(i));
            }
            if ((f & CRAFTABLE) != 0) {
                craftableSlots.add(inputs.get(i));
            }
        }
        return new PreviewSlots(missingSlots, craftableSlots, anyResolved);
    }

    private static int playerInventoryHash(MEStorageMenu container) {
        var items = container.getPlayerInventory().items;
        int h = 1;
        for (int slot = 0; slot < items.size(); slot++) {
            var stack = items.get(slot);
            int stackHash = stack.isEmpty() ? 0 : stack.hasTag() ? stack.getTag().hashCode() : 0;
            h = 31 * h + stackHash;
            h = 31 * h + stack.getCount();
            h = 31 * h + (container.isPlayerInventorySlotLocked(slot) ? 1 : 0);
        }
        return h;
    }

    private static List<IRecipeSlotView> collectInputSlots(IRecipeSlotsView recipeSlots) {
        return recipeSlots.getSlotViews(RecipeIngredientRole.INPUT).stream()
                .filter(TerminalJeRecipeTransferPreview::hasItemStack)
                .toList();
    }

    private static boolean hasCraftableAlternative(MEStorageMenu container, Ingredient ingredient) {
        return ClientRepoCraftableIndex.hasCraftableItemMatchingIngredient(container, ingredient);
    }

    private static boolean hasItemStack(IRecipeSlotView slotView) {
        return slotView.getDisplayedItemStack().isPresent() || slotView.getItemStacks().findAny().isPresent();
    }

    private static Ingredient toIngredient(IRecipeSlotView slotView) {
        return Ingredient.of(slotView.getItemStacks().map(ItemStack::copy));
    }

    private static ItemStack getDisplayedStack(IRecipeSlotView slotView) {
        return slotView.getDisplayedItemStack()
                .or(() -> slotView.getItemStacks().findFirst())
                .orElse(ItemStack.EMPTY);
    }
}
