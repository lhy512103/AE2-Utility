package com.lhy.ae2utility.integration.ae2;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.world.item.ItemStack;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;

import com.lhy.ae2utility.item.NbtTearCardItem;
import com.lhy.ae2utility.item.RedstoneSignalCardItem;

/**
 * Per-provider feature-card snapshot. Dedicated slots invalidate it immediately;
 * external upgrade inventories are refreshed at a low frequency because their
 * implementations do not expose a common change listener.
 */
public final class PatternProviderFeatureCardCache {
    private static final long REFRESH_INTERVAL_NANOS = 1_000_000_000L;

    private static final Class<?> COMPAT_PROVIDER_TYPE = loadCompatProviderType();
    private static final Method COMPAT_UPGRADES_GETTER = findCompatUpgradesGetter();
    private static final ClassValue<Optional<Method>> REFLECTIVE_UPGRADES_GETTERS = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            try {
                return Optional.of(type.getMethod("getUpgrades"));
            } catch (ReflectiveOperationException ignored) {
                return Optional.empty();
            }
        }
    };

    private final Object owner;
    private final Supplier<ItemStack> dedicatedSlot;

    private ItemStack nbtTearCard = ItemStack.EMPTY;
    private ItemStack redstoneSignalCard = ItemStack.EMPTY;
    private long refreshAfterNanos = Long.MIN_VALUE;

    public PatternProviderFeatureCardCache(Object owner, Supplier<ItemStack> dedicatedSlot) {
        this.owner = owner;
        this.dedicatedSlot = dedicatedSlot;
    }

    public ItemStack nbtTearCard() {
        refreshIfNeeded();
        return nbtTearCard;
    }

    public ItemStack redstoneSignalCard() {
        refreshIfNeeded();
        return redstoneSignalCard;
    }

    public void invalidate() {
        refreshAfterNanos = Long.MIN_VALUE;
    }

    private void refreshIfNeeded() {
        long now = System.nanoTime();
        if (now < refreshAfterNanos) {
            return;
        }
        refreshAfterNanos = now + REFRESH_INTERVAL_NANOS;

        CardPair dedicated = scanDedicatedSlot();
        CardPair standard = scanStandardUpgrades();
        CardPair reflected = owner instanceof IUpgradeableObject ? CardPair.EMPTY : scanReflectiveUpgrades();
        CardPair compat = scanCompatUpgrades();

        nbtTearCard = firstPresent(dedicated.nbtTearCard(), compat.nbtTearCard(),
                standard.nbtTearCard(), reflected.nbtTearCard());
        redstoneSignalCard = firstPresent(dedicated.redstoneSignalCard(), standard.redstoneSignalCard(),
                reflected.redstoneSignalCard(), compat.redstoneSignalCard());
    }

    private CardPair scanDedicatedSlot() {
        try {
            ItemStack stack = dedicatedSlot.get();
            return CardPair.fromSingleStack(stack);
        } catch (Throwable ignored) {
            return CardPair.EMPTY;
        }
    }

    private CardPair scanStandardUpgrades() {
        if (!(owner instanceof IUpgradeableObject upgradeableObject)) {
            return CardPair.EMPTY;
        }
        try {
            return scanInventory(upgradeableObject.getUpgrades());
        } catch (Throwable ignored) {
            return CardPair.EMPTY;
        }
    }

    private CardPair scanReflectiveUpgrades() {
        try {
            Optional<Method> getter = REFLECTIVE_UPGRADES_GETTERS.get(owner.getClass());
            if (getter.isEmpty()) {
                return CardPair.EMPTY;
            }
            Object inventory = getter.get().invoke(owner);
            return inventory instanceof IUpgradeInventory upgrades ? scanInventory(upgrades) : CardPair.EMPTY;
        } catch (Throwable ignored) {
            return CardPair.EMPTY;
        }
    }

    private CardPair scanCompatUpgrades() {
        if (COMPAT_PROVIDER_TYPE == null || COMPAT_UPGRADES_GETTER == null || !COMPAT_PROVIDER_TYPE.isInstance(owner)) {
            return CardPair.EMPTY;
        }
        try {
            Object inventory = COMPAT_UPGRADES_GETTER.invoke(owner);
            return inventory instanceof IUpgradeInventory upgrades ? scanInventory(upgrades) : CardPair.EMPTY;
        } catch (Throwable ignored) {
            return CardPair.EMPTY;
        }
    }

    private static CardPair scanInventory(IUpgradeInventory upgrades) {
        if (upgrades == null) {
            return CardPair.EMPTY;
        }
        ItemStack nbtTear = ItemStack.EMPTY;
        ItemStack redstone = ItemStack.EMPTY;
        for (ItemStack stack : upgrades) {
            if (nbtTear.isEmpty() && stack.getItem() instanceof NbtTearCardItem) {
                nbtTear = stack;
            } else if (redstone.isEmpty() && stack.getItem() instanceof RedstoneSignalCardItem) {
                redstone = stack;
            }
            if (!nbtTear.isEmpty() && !redstone.isEmpty()) {
                break;
            }
        }
        return new CardPair(nbtTear, redstone);
    }

    private static ItemStack firstPresent(ItemStack... candidates) {
        for (ItemStack candidate : candidates) {
            if (candidate != null && !candidate.isEmpty()) {
                return candidate;
            }
        }
        return ItemStack.EMPTY;
    }

    private static Class<?> loadCompatProviderType() {
        try {
            return Class.forName("com.extendedae_plus.api.bridge.CompatUpgradeProvider");
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static Method findCompatUpgradesGetter() {
        if (COMPAT_PROVIDER_TYPE == null) {
            return null;
        }
        try {
            return COMPAT_PROVIDER_TYPE.getMethod("eap$getCompatUpgrades");
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private record CardPair(ItemStack nbtTearCard, ItemStack redstoneSignalCard) {
        private static final CardPair EMPTY = new CardPair(ItemStack.EMPTY, ItemStack.EMPTY);

        private static CardPair fromSingleStack(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return EMPTY;
            }
            if (stack.getItem() instanceof NbtTearCardItem) {
                return new CardPair(stack, ItemStack.EMPTY);
            }
            if (stack.getItem() instanceof RedstoneSignalCardItem) {
                return new CardPair(ItemStack.EMPTY, stack);
            }
            return EMPTY;
        }
    }
}