package com.lhy.ae2utility.menu;

import java.util.function.Predicate;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * 样板供应器上的 AE2U 功能卡槽。准入策略由调用方显式提供，避免把其它模组的全局升级卡
 * 误路由到本槽。
 * <p>
 * 继承自 {@link SlotItemHandler} 而非 {@link appeng.menu.slot.AppEngSlot}，
 * 需显式 override {@link #isActive()} 为 {@code true}，确保 AE2 的 {@code UpgradesPanel}
 * 在 {@code getUpgradeSlotCount} 和 {@code updateBeforeRender} 中正确计入本槽。
 */
public final class PatternProviderTearSlot extends SlotItemHandler {
    private final Predicate<ItemStack> validator;

    public PatternProviderTearSlot(IItemHandler handler, int index, int x, int y, Predicate<ItemStack> validator) {
        super(handler, index, x, y);
        this.validator = validator;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return validator.test(stack);
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
