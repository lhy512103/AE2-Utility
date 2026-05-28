package com.lhy.ae2utility.integration.ae2;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

import com.lhy.ae2utility.card.RedstoneSignalCardMode;
import com.lhy.ae2utility.item.RedstoneSignalCardItem;

public interface PatternProviderSignalAccess {
    int ae2utility$getSignalPulseUntilTick();

    void ae2utility$setSignalPulseUntilTick(int gameTime);

    boolean ae2utility$isContinuousSignalActive();

    void ae2utility$setContinuousSignalActive(boolean active);

    default boolean ae2utility$hasSignalPulse(long gameTime) {
        return ae2utility$isContinuousSignalActive() || ae2utility$getSignalPulseUntilTick() > gameTime;
    }

    default void ae2utility$triggerSignalPulse(long gameTime, int durationTicks) {
        long end = gameTime + Math.max(1, durationTicks);
        int clamped = end > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) end;
        if (clamped > ae2utility$getSignalPulseUntilTick()) {
            ae2utility$setSignalPulseUntilTick(clamped);
        }
    }

    default ItemStack ae2utility$getRedstoneSignalCardStack() {
        if (!(this instanceof NbtTearLogicAccess access)) {
            return ItemStack.EMPTY;
        }
        ItemStack dedicated = access.ae2utility$getTearHandler().getStackInSlot(0);
        if (dedicated.getItem() instanceof RedstoneSignalCardItem) {
            return dedicated;
        }

        if ((Object) this instanceof IUpgradeableObject upgradeableObject) {
            ItemStack fromUpgrades = ae2utility$findRedstoneSignalCard(upgradeableObject.getUpgrades());
            if (!fromUpgrades.isEmpty()) {
                return fromUpgrades;
            }
        }

        ItemStack fromReflectedUpgrades = ae2utility$findRedstoneSignalCardReflectively(this);
        if (!fromReflectedUpgrades.isEmpty()) {
            return fromReflectedUpgrades;
        }

        try {
            Class<?> compatProvider = Class.forName("com.extendedae_plus.api.bridge.CompatUpgradeProvider");
            if (compatProvider.isInstance(this)) {
                Object inventory = compatProvider.getMethod("eap$getCompatUpgrades").invoke(this);
                if (inventory instanceof IUpgradeInventory upgrades) {
                    ItemStack fromCompat = ae2utility$findRedstoneSignalCard(upgrades);
                    if (!fromCompat.isEmpty()) {
                        return fromCompat;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return ItemStack.EMPTY;
    }

    default boolean ae2utility$shouldEmitFor(RedstoneSignalCardMode mode) {
        ItemStack stack = ae2utility$getRedstoneSignalCardStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof com.lhy.ae2utility.item.RedstoneSignalCardItem)) {
            return false;
        }
        return stack.getOrDefault(com.lhy.ae2utility.init.ModDataComponents.REDSTONE_SIGNAL_CARD_MODE, RedstoneSignalCardMode.ORDER) == mode;
    }

    default boolean ae2utility$isUntilRecipeMode() {
        return ae2utility$shouldEmitFor(RedstoneSignalCardMode.UNTIL_RECIPE_COMPLETE);
    }

    default int ae2utility$resolveRedstoneOutputDurationTicks() {
        ItemStack stack = ae2utility$getRedstoneSignalCardStack();
        return com.lhy.ae2utility.item.RedstoneSignalCardItem.resolveOutputDurationTicks(stack);
    }

    default void ae2utility$triggerSignalPulseFromHost(Object host) {
        BlockEntity blockEntity = ae2utility$getHostBlockEntity(host);
        if (blockEntity == null) {
            return;
        }
        var level = blockEntity.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        boolean wasActive = ae2utility$hasSignalPulse(level.getGameTime());
        int durationTicks = ae2utility$resolveRedstoneOutputDurationTicks();
        ae2utility$triggerSignalPulse(level.getGameTime(), durationTicks);
        ae2utility$saveHostChanges(host);
        if (!wasActive) {
            level.updateNeighborsAt(blockEntity.getBlockPos(), blockEntity.getBlockState().getBlock());
            level.updateNeighbourForOutputSignal(blockEntity.getBlockPos(), blockEntity.getBlockState().getBlock());
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.scheduleTick(blockEntity.getBlockPos(), blockEntity.getBlockState().getBlock(), durationTicks);
            }
        }
    }

    default void ae2utility$enableContinuousSignalFromHost(Object host) {
        BlockEntity blockEntity = ae2utility$getHostBlockEntity(host);
        if (blockEntity == null) {
            return;
        }
        var level = blockEntity.getLevel();
        if (level == null || level.isClientSide || ae2utility$isContinuousSignalActive()) {
            return;
        }
        ae2utility$setContinuousSignalActive(true);
        ae2utility$setSignalPulseUntilTick(0);
        ae2utility$saveHostChanges(host);
        level.updateNeighborsAt(blockEntity.getBlockPos(), blockEntity.getBlockState().getBlock());
        level.updateNeighbourForOutputSignal(blockEntity.getBlockPos(), blockEntity.getBlockState().getBlock());
    }

    default void ae2utility$disableContinuousSignalFromHost(Object host) {
        BlockEntity blockEntity = ae2utility$getHostBlockEntity(host);
        if (blockEntity == null) {
            return;
        }
        var level = blockEntity.getLevel();
        if (level == null || level.isClientSide || !ae2utility$isContinuousSignalActive()) {
            return;
        }
        ae2utility$setContinuousSignalActive(false);
        ae2utility$setSignalPulseUntilTick(0);
        ae2utility$saveHostChanges(host);
        level.updateNeighborsAt(blockEntity.getBlockPos(), blockEntity.getBlockState().getBlock());
        level.updateNeighbourForOutputSignal(blockEntity.getBlockPos(), blockEntity.getBlockState().getBlock());
    }

    private static BlockEntity ae2utility$getHostBlockEntity(Object host) {
        if (host instanceof PatternProviderLogicHost logicHost) {
            return logicHost.getBlockEntity();
        }
        if (host == null) {
            return null;
        }
        try {
            Object result = host.getClass().getMethod("getBlockEntity").invoke(host);
            return result instanceof BlockEntity blockEntity ? blockEntity : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static void ae2utility$saveHostChanges(Object host) {
        if (host instanceof PatternProviderLogicHost logicHost) {
            logicHost.saveChanges();
            return;
        }
        if (host == null) {
            return;
        }
        try {
            host.getClass().getMethod("saveChanges").invoke(host);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static ItemStack ae2utility$findRedstoneSignalCard(IUpgradeInventory upgrades) {
        if (upgrades == null) {
            return ItemStack.EMPTY;
        }
        for (ItemStack stack : upgrades) {
            if (stack.getItem() instanceof RedstoneSignalCardItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack ae2utility$findRedstoneSignalCardReflectively(Object owner) {
        if (owner == null) {
            return ItemStack.EMPTY;
        }
        try {
            Object upgrades = owner.getClass().getMethod("getUpgrades").invoke(owner);
            return upgrades instanceof IUpgradeInventory inventory
                    ? ae2utility$findRedstoneSignalCard(inventory)
                    : ItemStack.EMPTY;
        } catch (ReflectiveOperationException ignored) {
            return ItemStack.EMPTY;
        }
    }
}
