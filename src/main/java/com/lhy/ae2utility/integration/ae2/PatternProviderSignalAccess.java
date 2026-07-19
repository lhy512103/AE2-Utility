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

    /** 上一 tick 的「活跃」状态（派发未排空 或 待返还产物未排空），供状态机检测边沿。不持久化。 */
    boolean ae2utility$getLastRedstoneActive();

    void ae2utility$setLastRedstoneActive(boolean active);

    default boolean ae2utility$hasSignalPulse(long gameTime) {
        return ae2utility$isContinuousSignalActive() || ae2utility$getSignalPulseUntilTick() > gameTime;
    }

    /**
     * 双边沿红石状态机：{@code active = busy || returnPending}，由各 pattern provider 的稳定 tick 点每 tick 驱动。
     * <ul>
     * <li>上升沿（派发开始）：UNTIL 拉高连续信号；否则 ORDER 发脉冲。</li>
     * <li>下降沿（派发 + 产物返还全部完成）：UNTIL 拉低；否则当 {@code allowCraftOnFall} 时 CRAFT 发脉冲。</li>
     * </ul>
     * CRAFT 默认走各 mixin 的逐键精确事件捕获（{@code allowCraftOnFall=false}）；仅 ae2lt（返还方法被删、无精确捕获）
     * 传 {@code true} 用下降沿兜底 CRAFT。
     *
     * @param host          {@link PatternProviderLogicHost} 或可反射取 getBlockEntity/saveChanges 的宿主
     * @param busy          {@code logic.isBusy()}（sendList 未排空）
     * @param returnPending {@code !logic.getReturnInv().isEmpty()}（待返还产物未排空）
     */
    default void ae2utility$tickRedstoneStateMachine(Object host, boolean busy, boolean returnPending,
            boolean allowCraftOnFall) {
        boolean active = busy || returnPending;
        if (com.lhy.ae2utility.debug.Ae2UtilityRedstoneSignalDebugLog.PULSE_TRACE) {
            ItemStack card = ae2utility$getRedstoneSignalCardStack();
            var mode = card.isEmpty() ? null : card.getOrDefault(com.lhy.ae2utility.init.ModDataComponents.REDSTONE_SIGNAL_CARD_MODE, RedstoneSignalCardMode.ORDER);
            com.lhy.ae2utility.debug.Ae2UtilityRedstoneSignalDebugLog.pulse(
                    "state host={} busy={} returnPending={} active={} previous={} card={} mode={} allowCraftOnFall={}",
                    host == null ? "null" : host.getClass().getName(), busy, returnPending, active,
                    ae2utility$getLastRedstoneActive(), card.isEmpty() ? "missing" : card.getItem(), mode, allowCraftOnFall);
        }
        if (active == ae2utility$getLastRedstoneActive()) {
            return;
        }
        ae2utility$setLastRedstoneActive(active);
        if (active) {
            if (ae2utility$isUntilRecipeMode()) {
                ae2utility$enableContinuousSignalFromHost(host);
            } else if (ae2utility$shouldEmitFor(RedstoneSignalCardMode.ORDER)) {
                ae2utility$triggerSignalPulseFromHost(host);
            }
        } else {
            if (ae2utility$isUntilRecipeMode()) {
                ae2utility$disableContinuousSignalFromHost(host);
            } else if (allowCraftOnFall && ae2utility$shouldEmitFor(RedstoneSignalCardMode.CRAFT)) {
                ae2utility$triggerSignalPulseFromHost(host);
            }
        }
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
        com.lhy.ae2utility.debug.Ae2UtilityRedstoneSignalDebugLog.pulse(
                "trigger pulse block={} pos={} wasActive={} durationTicks={} modeCard={}",
                blockEntity.getClass().getName(), blockEntity.getBlockPos(), wasActive, durationTicks,
                ae2utility$getRedstoneSignalCardStack().isEmpty() ? "missing" : "present");
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
