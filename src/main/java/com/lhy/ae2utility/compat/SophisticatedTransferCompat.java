package com.lhy.ae2utility.compat;

import java.util.LinkedHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.p3pp3rf1y.sophisticatedcore.controller.IControllableStorage;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IExtractResponseUpgrade;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import com.lhy.ae2utility.service.MeQuickTransferService.TransferSource;

/** Fast path for oversized Sophisticated Storage and placed backpack slots. */
public final class SophisticatedTransferCompat {
    private static final int MAX_SLOT_WRITES_PER_OPERATION = 64;

    private SophisticatedTransferCompat() {
    }

    public static @Nullable TransferSource findSource(ServerLevel level, BlockPos pos, IItemHandler exposedHandler) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof IControllableStorage storage)) {
            return null;
        }

        var wrapper = storage.getStorageWrapper();
        InventoryHandler inventory = wrapper.getInventoryHandler();
        if (inventory.getSlots() != exposedHandler.getSlots()
                || !wrapper.getUpgradeHandler().getWrappersThatImplementFromMainStorage(IExtractResponseUpgrade.class)
                        .isEmpty()) {
            return null;
        }
        return new SophisticatedItemSource(inventory, exposedHandler);
    }

    private static final class SophisticatedItemSource implements TransferSource {
        private final InventoryHandler inventory;
        private final IItemHandler exposedHandler;

        private SophisticatedItemSource(InventoryHandler inventory, IItemHandler exposedHandler) {
            this.inventory = inventory;
            this.exposedHandler = exposedHandler;
        }

        @Override
        public LinkedHashMap<AEKey, Long> available() {
            LinkedHashMap<AEKey, Long> result = new LinkedHashMap<>();
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                AEItemKey key = AEItemKey.of(stack);
                if (key != null && canExtract(slot, key)) {
                    result.merge(key, (long) stack.getCount(), Long::sum);
                }
            }
            return result;
        }

        @Override
        public long extract(AEKey key, long amount, boolean simulate) {
            if (!(key instanceof AEItemKey itemKey)) {
                return 0;
            }

            long remaining = amount;
            long extracted = 0;
            int writes = 0;
            for (int slot = 0; slot < inventory.getSlots() && remaining > 0; slot++) {
                ItemStack existing = inventory.getStackInSlot(slot);
                if (!itemKey.matches(existing) || !canExtract(slot, itemKey)) {
                    continue;
                }
                if (writes++ >= MAX_SLOT_WRITES_PER_OPERATION) {
                    break;
                }
                int taken = (int) Math.min(remaining, existing.getCount());
                if (!simulate) {
                    inventory.setStackInSlot(slot,
                            taken == existing.getCount() ? ItemStack.EMPTY : existing.copyWithCount(existing.getCount() - taken));
                }
                extracted += taken;
                remaining -= taken;
            }
            return extracted;
        }

        @Override
        public long insert(AEKey key, long amount) {
            if (!(key instanceof AEItemKey itemKey)) {
                return 0;
            }
            long remaining = amount;
            for (int slot = 0; slot < inventory.getSlots() && remaining > 0; slot++) {
                int request = (int) Math.min(Integer.MAX_VALUE, remaining);
                ItemStack left = inventory.insertItem(slot, itemKey.toStack(request), false);
                remaining -= request - left.getCount();
            }
            return amount - remaining;
        }

        private boolean canExtract(int slot, AEItemKey key) {
            ItemStack internal = inventory.getStackInSlot(slot);
            ItemStack exposed = exposedHandler.getStackInSlot(slot);
            if (internal.getCount() != exposed.getCount() || !ItemStack.isSameItemSameComponents(internal, exposed)) {
                return false;
            }
            ItemStack simulated = exposedHandler.extractItem(slot, 1, true);
            return key.matches(simulated);
        }
    }
}
