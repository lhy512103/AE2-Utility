package com.lhy.ae2utility.jei;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEItems;
import appeng.menu.me.common.MEStorageMenu;

import com.lhy.ae2utility.compat.WcwtCompat;

/**
 * 批量编码前的客户端空白样板预检，检查顺序与服务端消耗顺序一致：终端空白槽 → 背包 → 已打开终端的 ME 库存快照。
 * 看不到 ME 库存（未打开终端）时不拦截，交给服务端判断。
 */
public final class BlankPatternClientPrecheck {
    private BlankPatternClientPrecheck() {
    }

    public static boolean lacksAnyDetectableBlankPattern(LocalPlayer player) {
        if (player == null) {
            return false;
        }
        if (WcwtCompat.isPatternEncodingLikeMenu(player.containerMenu)) {
            for (Slot slot : player.containerMenu.slots) {
                if (isBlankPattern(slot.getItem())) {
                    return false;
                }
            }
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isBlankPattern(player.getInventory().getItem(i))) {
                return false;
            }
        }
        if (!(player.containerMenu instanceof MEStorageMenu menu)) {
            return false;
        }
        var clientRepo = menu.getClientRepo();
        if (clientRepo == null) {
            return false;
        }
        AEItemKey blankKey = AEItemKey.of(AEItems.BLANK_PATTERN);
        for (var entry : clientRepo.getAllEntries()) {
            if (blankKey.equals(entry.getWhat()) && entry.getStoredAmount() > 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBlankPattern(ItemStack stack) {
        return !stack.isEmpty() && AEItems.BLANK_PATTERN.isSameAs(stack);
    }
}
