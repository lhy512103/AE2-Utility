package com.lhy.ae2utility.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 白名单标记格：仅用于显示与同步到数据组件，不允许从背包放入/取出实体物品；
 * 写入途径为 {@link NbtTearCardMenu#applyGhostFilterSlot}、{@link NbtTearCardMenu#clicked} 中光标左/右键写入，
 * 以及菜单内空光标右键清除逻辑。
 */
final class MarkFilterSlot extends Slot {
    MarkFilterSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return 1;
    }
}
