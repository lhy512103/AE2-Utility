package com.lhy.ae2utility.integration.ae2;

import java.lang.reflect.Method;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderLogic;

import com.lhy.ae2utility.item.NbtTearCardItem;

/**
 * Implemented by mixin on {@link appeng.helpers.patternprovider.PatternProviderLogic}.
 */
public interface NbtTearLogicAccess {
    ItemStackHandler ae2utility$getTearHandler();

    default ItemStack ae2utility$getEffectiveTearCardStack() {
        ItemStack dedicated = ae2utility$getTearHandler().getStackInSlot(0);
        if (dedicated.getItem() instanceof NbtTearCardItem) {
            return dedicated;
        }

        ItemStack fromCompat = ae2utility$getCompatUpgradeInventory();
        if (!fromCompat.isEmpty()) {
            return fromCompat;
        }

        ItemStack fromUpgrades = ae2utility$getUpgradeableObjectInventory();
        if (!fromUpgrades.isEmpty()) {
            return fromUpgrades;
        }

        return ItemStack.EMPTY;
    }

    private ItemStack ae2utility$getUpgradeableObjectInventory() {
        if ((Object) this instanceof IUpgradeableObject upgradeableObject) {
            try {
                return ae2utility$findTearCard(upgradeableObject.getUpgrades());
            } catch (Throwable ignored) {
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack ae2utility$getCompatUpgradeInventory() {
        try {
            Class<?> compatProvider = Class.forName("com.extendedae_plus.api.bridge.CompatUpgradeProvider");
            if (!compatProvider.isInstance(this)) {
                return ItemStack.EMPTY;
            }
            Method getter = compatProvider.getMethod("eap$getCompatUpgrades");
            Object inventory = getter.invoke(this);
            if (inventory instanceof IUpgradeInventory upgrades) {
                return ae2utility$findTearCard(upgrades);
            }
        } catch (Throwable ignored) {
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack ae2utility$findTearCard(IUpgradeInventory upgrades) {
        if (upgrades == null) {
            return ItemStack.EMPTY;
        }
        for (ItemStack stack : upgrades) {
            if (stack.getItem() instanceof NbtTearCardItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
