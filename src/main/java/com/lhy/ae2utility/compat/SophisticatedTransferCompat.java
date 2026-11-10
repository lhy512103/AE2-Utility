package com.lhy.ae2utility.compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.p3pp3rf1y.sophisticatedcore.controller.IControllableStorage;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IExtractResponseUpgrade;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import com.lhy.ae2utility.service.MeQuickTransferService.TransferSource;

/** Fast path for oversized Sophisticated Storage and placed backpack slots. */
public final class SophisticatedTransferCompat {
    private static final int MAX_SLOT_WRITES_PER_OPERATION = 64;

    private SophisticatedTransferCompat() {}

    public static @Nullable TransferSource findSource(ServerLevel level, BlockPos pos,
            ResourceHandler<ItemResource> exposedHandler) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof IControllableStorage storage)) {
            return null;
        }
        var wrapper = storage.getStorageWrapper();
        InventoryHandler inventory = wrapper.getInventoryHandler();
        if (inventory.size() != exposedHandler.size()
                || !wrapper.getUpgradeHandler().getWrappersThatImplementFromMainStorage(IExtractResponseUpgrade.class)
                        .isEmpty()) {
            return null;
        }
        return new SophisticatedItemSource(inventory, exposedHandler);
    }

    private static final class SophisticatedItemSource implements TransferSource {
        private final InventoryHandler inventory;
        private final ResourceHandler<ItemResource> exposedHandler;
        private final Map<AEKey, List<Integer>> slotsByKey = new HashMap<>();
        private final Map<AEKey, Integer> slotCursors = new HashMap<>();

        private SophisticatedItemSource(InventoryHandler inventory, ResourceHandler<ItemResource> exposedHandler) {
            this.inventory = inventory;
            this.exposedHandler = exposedHandler;
        }

        @Override
        public LinkedHashMap<AEKey, Long> available() {
            LinkedHashMap<AEKey, Long> result = new LinkedHashMap<>();
            slotsByKey.clear();
            slotCursors.clear();
            try (Transaction transaction = Transaction.openRoot()) {
                for (int slot = 0; slot < inventory.size(); slot++) {
                    ItemResource resource = inventory.getResource(slot);
                    AEItemKey key = AEItemKey.of(resource.toStack());
                    if (key != null && canExtract(slot, resource, transaction)) {
                        result.merge(key, inventory.getAmountAsLong(slot), Long::sum);
                        slotsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(slot);
                    }
                }
            }
            return result;
        }

        @Override
        public long extract(AEKey key, long amount, boolean simulate) {
            if (!(key instanceof AEItemKey itemKey) || amount <= 0) {
                return 0;
            }
            List<Integer> slots = slotsByKey.get(key);
            if (slots == null) {
                return 0;
            }
            ItemResource resource = ItemResource.of(itemKey.toStack());
            long extracted = 0;
            int cursor = slotCursors.getOrDefault(key, 0);
            int writes = 0;
            try (Transaction transaction = Transaction.openRoot()) {
                while (cursor < slots.size() && extracted < amount && writes < MAX_SLOT_WRITES_PER_OPERATION) {
                    int slot = slots.get(cursor);
                    if (slot >= inventory.size() || !resource.equals(inventory.getResource(slot))
                            || !canExtract(slot, resource, transaction)) {
                        cursor++;
                        continue;
                    }
                    int request = (int) Math.min(Integer.MAX_VALUE,
                            Math.min(amount - extracted, inventory.getAmountAsLong(slot)));
                    int taken = inventory.extract(slot, resource, request, transaction);
                    writes++;
                    extracted += taken;
                    if (taken == 0 || inventory.getAmountAsLong(slot) == 0) {
                        cursor++;
                    }
                }
                if (!simulate) {
                    transaction.commit();
                }
            }
            if (!simulate) {
                slotCursors.put(key, cursor);
            }
            return extracted;
        }

        @Override
        public long insert(AEKey key, long amount) {
            if (!(key instanceof AEItemKey itemKey) || amount <= 0) {
                return 0;
            }
            ItemResource resource = ItemResource.of(itemKey.toStack());
            long inserted = 0;
            int writes = 0;
            try (Transaction transaction = Transaction.openRoot()) {
                for (int slot = 0; slot < inventory.size() && inserted < amount
                        && writes < MAX_SLOT_WRITES_PER_OPERATION; slot++) {
                    int request = (int) Math.min(Integer.MAX_VALUE, amount - inserted);
                    inserted += inventory.insert(slot, resource, request, transaction);
                    writes++;
                }
                transaction.commit();
            }
            return inserted;
        }

        private boolean canExtract(int slot, ItemResource resource, TransactionContext parent) {
            if (slot >= exposedHandler.size()
                    || inventory.getAmountAsLong(slot) != exposedHandler.getAmountAsLong(slot)
                    || !resource.equals(exposedHandler.getResource(slot))) {
                return false;
            }
            // Check automation permission without committing the probe or consuming a unit.
            try (Transaction probe = Transaction.open(parent)) {
                return exposedHandler.extract(slot, resource, 1, probe) == 1;
            }
        }
    }
}
