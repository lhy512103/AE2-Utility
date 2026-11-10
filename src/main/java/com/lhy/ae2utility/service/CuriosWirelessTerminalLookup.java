package com.lhy.ae2utility.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import appeng.helpers.WirelessCraftingTerminalMenuHost;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.locator.MenuLocator;
import com.lhy.ae2utility.compat.ModCapabilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** Optional Curios facade. Each optional API lives behind its own metadata-gated adapter. */
public final class CuriosWirelessTerminalLookup {
    private CuriosWirelessTerminalLookup() {
    }

    public static boolean hasCandidate(Player player) {
        return !findCandidates(player).isEmpty();
    }

    public static List<Candidate> findCandidates(Player player) {
        if (player == null || !ModCapabilities.hasCurios()
                || (!ModCapabilities.hasAE2WTLib() && !ModCapabilities.hasWcwt())) {
            return List.of();
        }
        return CuriosAdapter.findCandidates(player);
    }

    public static @Nullable WirelessTerminalMenuHost locate(Player player, Candidate candidate) {
        if (player == null || candidate == null) return null;
        MenuLocator locator = locator(candidate.slotId(), candidate.slotIndex(), isWcwtStack(candidate.stack()),
                ModCapabilities::isLoaded);
        if (locator == null) return null;
        WirelessTerminalMenuHost host = locator.locate(player, WirelessTerminalMenuHost.class);
        return host != null ? host : locator.locate(player, WirelessCraftingTerminalMenuHost.class);
    }

    // Metadata-only selection is deliberately testable without loading any optional API or target.
    static LocatorProvider locatorProvider(boolean wcwtStack, Predicate<String> modLoaded) {
        if (!modLoaded.test(ModCapabilities.CURIOS)) return LocatorProvider.NONE;
        if (wcwtStack && (modLoaded.test(ModCapabilities.WCWT)
                || modLoaded.test(ModCapabilities.LEGACY_WCWT))) {
            return LocatorProvider.WCWT;
        }
        return modLoaded.test(ModCapabilities.AE2_WIRELESS_TERMINALS)
                ? LocatorProvider.AE2WTLIB : LocatorProvider.NONE;
    }

    static @Nullable MenuLocator locator(String slotId, int slotIndex, boolean wcwtStack,
            Predicate<String> modLoaded) {
        return switch (locatorProvider(wcwtStack, modLoaded)) {
            case NONE -> null;
            case AE2WTLIB -> Ae2wtlibAdapter.locator(slotId, slotIndex);
            case WCWT -> WcwtAdapter.locator(slotId, slotIndex);
        };
    }

    private static boolean isWcwtStack(ItemStack stack) {
        return stack != null && stack.getItem().getClass().getName().startsWith("com.lhy.wcwt.");
    }

    public record Candidate(String slotId, int slotIndex, ItemStack stack) {
    }

    enum LocatorProvider {
        NONE, AE2WTLIB, WCWT
    }

    public static @Nullable IItemHandler items(Player player) {
        // Equipped inventory is useful for blank-pattern lookup even without wireless-terminal addons.
        if (player == null || !ModCapabilities.hasCurios()) return null;
        return CuriosAdapter.items(player);
    }

    private static final class CuriosAdapter {
        private static List<Candidate> findCandidates(Player player) {
            var inventory = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve().orElse(null);
            if (inventory == null) return List.of();
            List<Candidate> result = new ArrayList<>();
            for (Map.Entry<String, top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler> entry
                    : inventory.getCurios().entrySet()) {
                var stacks = entry.getValue();
                for (int slot = 0; slot < stacks.getSlots(); slot++) {
                    ItemStack stack = stacks.getStacks().getStackInSlot(slot);
                    if (WirelessEncodeTerminalItems.mayProvideWirelessEncoding(stack)
                            && locatorProvider(isWcwtStack(stack), ModCapabilities::isLoaded) != LocatorProvider.NONE) {
                        result.add(new Candidate(entry.getKey(), slot, stack));
                    }
                }
            }
            return result;
        }

        private static @Nullable IItemHandler items(Player player) {
            var inventory = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve().orElse(null);
            return inventory == null ? null : inventory.getEquippedCurios();
        }
    }

    private static final class Ae2wtlibAdapter {
        private static MenuLocator locator(String slotId, int slotIndex) {
            return new de.mari_023.ae2wtlib.curio.CurioLocator(slotId, slotIndex);
        }
    }

    private static final class WcwtAdapter {
        private static MenuLocator locator(String slotId, int slotIndex) {
            return new com.lhy.wcwt.menu.locator.WcwtCurioLocator(slotId, slotIndex);
        }
    }
}
