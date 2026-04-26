package com.lhy.ae2utility.menu;

import net.minecraft.world.item.ItemStack;
import appeng.api.upgrades.Upgrades;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * 样板供应器上的“额外升级槽”；仍然作为撕裂卡逻辑的承载槽使用，但准入规则与普通升级槽一致，
 * 以便在 EAEP / AppFlux 的升级面板里表现为正常的第 3 个升级槽。
 * <p>
 * 继承自 {@link SlotItemHandler} 而非 {@link appeng.menu.slot.AppEngSlot}，
 * 需显式 override {@link #isActive()} 为 {@code true}，确保 AE2 的 {@code UpgradesPanel}
 * 在 {@code getUpgradeSlotCount} 和 {@code updateBeforeRender} 中正确计入本槽。
 */
public final class PatternProviderTearSlot extends SlotItemHandler {
    public PatternProviderTearSlot(IItemHandler handler, int index, int x, int y) {
        super(handler, index, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !stack.isEmpty() && Upgrades.isUpgradeCardItem(stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return 1;
    }

    /** UpgradesPanel 通过 isActive() 决定是否计入面板布局；显式返回 true 确保始终被管理。 */
    @Override
    public boolean isActive() {
        return true;
    }
}
