package com.lhy.ae2utility.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import appeng.helpers.WirelessCraftingTerminalMenuHost;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.locator.MenuLocator;
import com.lhy.wcwt.menu.locator.WcwtCurioLocator;
import de.mari_023.ae2wtlib.curio.CurioLocator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/** Optional Curios lookup kept outside JEI render paths. */
public final class CuriosWirelessTerminalLookup {
    private static final String CURIOS_MOD_ID = "curios";
    private static final String WCWT_MOD_ID = "wcwt";
    private static final String LEGACY_WCWT_MOD_ID = "wireless_comprehensive_work_terminal";

    private CuriosWirelessTerminalLookup() {
    }

    public static boolean hasCandidate(Player player) {
        if (!isCuriosLoaded()) {
            return false;
        }
        var handler = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        if (handler == null) {
            return false;
        }
        for (ICurioStacksHandler stacksHandler : handler.getCurios().values()) {
            for (int slot = 0; slot < stacksHandler.getSlots(); slot++) {
                if (WirelessEncodeTerminalItems.mayProvideWirelessEncoding(
                        stacksHandler.getStacks().getStackInSlot(slot))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static List<Candidate> findCandidates(Player player) {
        if (!isCuriosLoaded()) {
            return List.of();
        }
        var handler = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        if (handler == null) {
            return List.of();
        }

        List<Candidate> result = new ArrayList<>();
        for (Map.Entry<String, ICurioStacksHandler> entry : handler.getCurios().entrySet()) {
            ICurioStacksHandler stacksHandler = entry.getValue();
            for (int slot = 0; slot < stacksHandler.getSlots(); slot++) {
                ItemStack stack = stacksHandler.getStacks().getStackInSlot(slot);
                if (WirelessEncodeTerminalItems.mayProvideWirelessEncoding(stack)) {
                    result.add(new Candidate(entry.getKey(), slot, stack));
                }
            }
        }
        return result;
    }

    public static @Nullable WirelessTerminalMenuHost locate(Player player, Candidate candidate) {
        MenuLocator locator;
        if (isWcwtLoaded() && WirelessEncodeTerminalItems.isWcwt(candidate.stack())) {
            locator = new WcwtCurioLocator(candidate.slotId(), candidate.slotIndex());
        } else {
            locator = new CurioLocator(candidate.slotId(), candidate.slotIndex());
        }

        WirelessTerminalMenuHost host = locator.locate(player, WirelessTerminalMenuHost.class);
        if (host != null) {
            return host;
        }
        return locator.locate(player, WirelessCraftingTerminalMenuHost.class);
    }

    private static boolean isCuriosLoaded() {
        return ModList.get().isLoaded(CURIOS_MOD_ID);
    }

    private static boolean isWcwtLoaded() {
        return ModList.get().isLoaded(WCWT_MOD_ID) || ModList.get().isLoaded(LEGACY_WCWT_MOD_ID);
    }

    public record Candidate(String slotId, int slotIndex, ItemStack stack) {
    }
}
