package com.lhy.ae2utility.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.ids.AEComponents;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import com.lhy.ae2utility.Ae2UtilityServerConfig;
import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.compat.MekanismTransferCompat;
import com.lhy.ae2utility.compat.ModCapabilities;
import com.lhy.ae2utility.compat.SophisticatedTransferCompat;
import com.lhy.ae2utility.init.ModItems;

/** Server-side transfer engine for the ME Quick Transfer Tool. */
public final class MeQuickTransferService {
    private static final int MAX_OPERATIONS_PER_TICK = 256;
    private static final int MAX_HANDLER_CALLS_PER_OPERATION = 64;
    private static final long JOB_TIME_BUDGET_NANOS = 4_000_000L;
    private static final Map<UUID, TransferJob> JOBS = new HashMap<>();

    private MeQuickTransferService() {
    }

    public static void registerTickHandler() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> tick());
    }

    public static boolean cancel(UUID playerId) {
        return JOBS.remove(playerId) != null;
    }

    public static void stop(ServerPlayer player) {
        tell(player, cancel(player.getUUID())
                ? "message.ae2utility.me_quick_transfer_stopped"
                : "message.ae2utility.me_quick_transfer_not_running");
    }

    public static void start(ServerPlayer player, ItemStack tool, BlockPos pos, Direction side, boolean transferAll) {
        if (tool.getItem() != ModItems.ME_QUICK_TRANSFER.get()) {
            return;
        }
        if (JOBS.containsKey(player.getUUID())) {
            tell(player, "message.ae2utility.me_quick_transfer_busy");
            return;
        }

        IGrid grid = resolveLinkedGrid(player, tool);
        if (grid == null) {
            tell(player, "message.ae2utility.me_quick_transfer_no_network");
            return;
        }

        List<TransferSource> sources = findSources(player.level(), pos, side);
        List<TransferEntry> entries = new ArrayList<>();
        for (TransferSource source : sources) {
            for (var entry : source.available().entrySet()) {
                if (entry.getValue() > 0) {
                    entries.add(new TransferEntry(source, entry.getKey(), entry.getValue()));
                }
            }
        }
        if (entries.isEmpty()) {
            tell(player, "message.ae2utility.me_quick_transfer_empty");
            return;
        }

        MEStorage storage = grid.getStorageService().getInventory();
        IActionSource actionSource = IActionSource.ofPlayer(player);
        if (!transferAll) {
            TransferEntry selected = null;
            for (TransferEntry candidate : entries) {
                candidate.remaining = Math.min(candidate.remaining, groupAmount(candidate.key));
                if (canMove(List.of(candidate), storage, actionSource)) {
                    selected = candidate;
                    break;
                }
            }
            if (selected == null) {
                tell(player, "message.ae2utility.me_quick_transfer_cannot_move");
                return;
            }
            entries = List.of(selected);
        }

        if (!canMove(entries, storage, actionSource)) {
            tell(player, "message.ae2utility.me_quick_transfer_cannot_move");
            return;
        }

        double energyFee = transferAll
                ? Ae2UtilityServerConfig.meQuickTransferAllEnergy()
                : Ae2UtilityServerConfig.meQuickTransferGroupEnergy();
        if (grid.getEnergyService().extractAEPower(energyFee, Actionable.SIMULATE, PowerMultiplier.CONFIG) < energyFee) {
            tell(player, "message.ae2utility.me_quick_transfer_no_energy", energyFee);
            return;
        }
        grid.getEnergyService().extractAEPower(energyFee, Actionable.MODULATE, PowerMultiplier.CONFIG);

        TransferJob job = new TransferJob(player, storage, entries, actionSource, player.level(), pos);
        JOBS.put(player.getUUID(), job);
        tell(player, transferAll ? "message.ae2utility.me_quick_transfer_started_all"
                : "message.ae2utility.me_quick_transfer_started_group", entries.size());

        if (!transferAll) {
            tickJob(job, Long.MAX_VALUE);
            JOBS.remove(player.getUUID());
        }
    }

    private static void tick() {
        if (JOBS.isEmpty()) {
            return;
        }
        for (TransferJob job : List.copyOf(JOBS.values())) {
            if (job.player.isRemoved()) {
                JOBS.remove(job.player.getUUID());
                continue;
            }
            tickJob(job, System.nanoTime() + JOB_TIME_BUDGET_NANOS);
            if (job.isFinished()) {
                JOBS.remove(job.player.getUUID());
            }
        }
    }

    private static void tickJob(TransferJob job, long deadlineNanos) {
        if (!job.isSourceValid()) {
            job.index = job.entries.size();
            tell(job.player, "message.ae2utility.me_quick_transfer_source_changed");
            return;
        }

        int processed = 0;
        while (!job.isFinished() && processed < MAX_OPERATIONS_PER_TICK
                && System.nanoTime() < deadlineNanos) {
            processed++;
            TransferEntry entry = job.entries.get(job.index);
            long moved = move(entry.source, job.storage, entry.key, entry.remaining, job.actionSource);
            entry.remaining -= moved;
            job.transferred += moved;
            if (entry.remaining <= 0 || moved <= 0) {
                job.index++;
            }
        }

        if (job.isFinished()) {
            tell(job.player, "message.ae2utility.me_quick_transfer_finished", job.transferred);
        } else if (job.player.level().getGameTime() % 20 == 0) {
            tell(job.player, "message.ae2utility.me_quick_transfer_progress", job.index, job.entries.size(), job.transferred);
        }
    }

    private static long move(TransferSource source, MEStorage storage, AEKey key, long requested,
            IActionSource actionSource) {
        if (requested <= 0) {
            return 0;
        }
        long extractable = source.extract(key, requested, true);
        if (extractable <= 0) {
            return 0;
        }
        long accepted = storage.insert(key, extractable, Actionable.SIMULATE, actionSource);
        if (accepted <= 0) {
            return 0;
        }

        long extracted = source.extract(key, accepted, false);
        if (extracted <= 0) {
            return 0;
        }
        long inserted = storage.insert(key, extracted, Actionable.MODULATE, actionSource);
        if (inserted < extracted) {
            long remainder = extracted - inserted;
            long retried = storage.insert(key, remainder, Actionable.MODULATE, actionSource);
            inserted += retried;
            remainder -= retried;
            if (remainder > 0) {
                long restored = source.insert(key, remainder);
                if (restored < remainder) {
                    Ae2UtilityMod.LOGGER.error(
                            "ME quick transfer could not restore {} units of {} after a changed insertion result",
                            remainder - restored, key);
                }
            }
        }
        return inserted;
    }

    private static boolean canMove(List<TransferEntry> entries, MEStorage storage, IActionSource actionSource) {
        for (TransferEntry entry : entries) {
            long extractable = entry.source.extract(entry.key, entry.remaining, true);
            if (extractable > 0 && storage.insert(entry.key, extractable, Actionable.SIMULATE, actionSource) > 0) {
                return true;
            }
        }
        return false;
    }

    private static long groupAmount(AEKey key) {
        if (key instanceof AEItemKey itemKey) {
            return itemKey.getMaxStackSize();
        }
        return key.getAmountPerUnit();
    }

    private static List<TransferSource> findSources(ServerLevel level, BlockPos pos, Direction side) {
        List<TransferSource> sources = new ArrayList<>();
        ResourceHandler<ItemResource> items = level.getCapability(Capabilities.Item.BLOCK, pos, side);
        if (items == null) {
            items = level.getCapability(Capabilities.Item.BLOCK, pos, null);
        }
        if (items != null) {
            TransferSource itemSource = null;
            if (ModCapabilities.hasSophisticatedCore()) {
                try {
                    ResourceHandler<ItemResource> unsided = level.getCapability(Capabilities.Item.BLOCK, pos, null);
                    itemSource = SophisticatedTransferCompat.findSource(level, pos, unsided != null ? unsided : items);
                } catch (LinkageError error) {
                    Ae2UtilityMod.LOGGER.warn("Sophisticated storage fast transfer is unavailable", error);
                }
            }
            sources.add(itemSource != null ? itemSource : new ItemSource(items));
        }

        ResourceHandler<FluidResource> fluids = level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
        if (fluids == null) {
            fluids = level.getCapability(Capabilities.Fluid.BLOCK, pos, null);
        }
        if (fluids != null) {
            sources.add(new FluidSource(fluids));
        }

        if (ModCapabilities.hasMekanism() && ModCapabilities.hasAppliedMekanistics()) {
            try {
                TransferSource chemicals = MekanismTransferCompat.findSource(level, pos, side);
                if (chemicals != null) {
                    sources.add(chemicals);
                }
            } catch (LinkageError error) {
                Ae2UtilityMod.LOGGER.warn("Mekanism chemical transfer is unavailable", error);
            }
        }
        return sources;
    }

    private static @Nullable IGrid resolveLinkedGrid(ServerPlayer player, ItemStack tool) {
        GlobalPos target = tool.get(AEComponents.WIRELESS_LINK_TARGET);
        if (target == null) {
            return null;
        }
        ServerLevel linkedLevel = player.level().getServer().getLevel(target.dimension());
        if (linkedLevel == null) {
            return null;
        }
        if (!linkedLevel.isLoaded(target.pos())
                || !(linkedLevel.getBlockEntity(target.pos()) instanceof IWirelessAccessPoint accessPoint)
                || !accessPoint.isActive()) {
            return null;
        }
        return accessPoint.getGrid();
    }

    private static void tell(ServerPlayer player, String key, Object... args) {
        player.sendOverlayMessage(Component.translatable(key, args));
    }

    public interface TransferSource {
        LinkedHashMap<AEKey, Long> available();

        long extract(AEKey key, long amount, boolean simulate);

        long insert(AEKey key, long amount);
    }

    private static final class TransferEntry {
        private final TransferSource source;
        private final AEKey key;
        private long remaining;

        private TransferEntry(TransferSource source, AEKey key, long remaining) {
            this.source = source;
            this.key = key;
            this.remaining = remaining;
        }
    }

    private static final class TransferJob {
        private final ServerPlayer player;
        private final MEStorage storage;
        private final List<TransferEntry> entries;
        private final IActionSource actionSource;
        private final ServerLevel sourceLevel;
        private final BlockPos sourcePos;
        private final Block sourceBlock;
        private final @Nullable BlockEntity sourceBlockEntity;
        private int index;
        private long transferred;

        private TransferJob(ServerPlayer player, MEStorage storage, List<TransferEntry> entries,
                IActionSource actionSource, ServerLevel sourceLevel, BlockPos sourcePos) {
            this.player = player;
            this.storage = storage;
            this.entries = entries;
            this.actionSource = actionSource;
            this.sourceLevel = sourceLevel;
            this.sourcePos = sourcePos.immutable();
            this.sourceBlock = sourceLevel.getBlockState(sourcePos).getBlock();
            this.sourceBlockEntity = sourceLevel.getBlockEntity(sourcePos);
        }

        private boolean isFinished() {
            return index >= entries.size();
        }

        private boolean isSourceValid() {
            return sourceLevel.isLoaded(sourcePos)
                    && sourceLevel.getBlockState(sourcePos).is(sourceBlock)
                    && sourceLevel.getBlockEntity(sourcePos) == sourceBlockEntity;
        }
    }

    /** Keeps simulation changes inside an aborted transaction, including across multiple indices. */
    private abstract static class ResourceSource<T extends Resource> implements TransferSource {
        private final ResourceHandler<T> handler;
        private final Map<AEKey, List<Integer>> indicesByKey = new HashMap<>();
        private final Map<AEKey, Integer> cursors = new HashMap<>();

        private ResourceSource(ResourceHandler<T> handler) {
            this.handler = handler;
        }

        protected abstract @Nullable AEKey keyOf(T resource);
        protected abstract @Nullable T resourceOf(AEKey key);

        @Override
        public LinkedHashMap<AEKey, Long> available() {
            LinkedHashMap<AEKey, Long> result = new LinkedHashMap<>();
            indicesByKey.clear();
            cursors.clear();
            for (int index = 0; index < handler.size(); index++) {
                T resource = handler.getResource(index);
                if (resource.isEmpty()) {
                    continue;
                }
                AEKey key = keyOf(resource);
                long amount = handler.getAmountAsLong(index);
                if (key != null && amount > 0) {
                    result.merge(key, amount, Long::sum);
                    indicesByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(index);
                }
            }
            return result;
        }

        @Override
        public long extract(AEKey key, long amount, boolean simulate) {
            T resource = resourceOf(key);
            List<Integer> indices = indicesByKey.get(key);
            if (resource == null || resource.isEmpty() || indices == null || amount <= 0) {
                return 0;
            }
            long extracted = 0;
            int cursor = cursors.getOrDefault(key, 0);
            int calls = 0;
            try (Transaction transaction = Transaction.openRoot()) {
                while (cursor < indices.size() && extracted < amount && calls < MAX_HANDLER_CALLS_PER_OPERATION) {
                    int index = indices.get(cursor);
                    if (index >= handler.size() || !resource.equals(handler.getResource(index))) {
                        cursor++;
                        continue;
                    }
                    int request = (int) Math.min(Integer.MAX_VALUE, amount - extracted);
                    int moved = handler.extract(index, resource, request, transaction);
                    calls++;
                    extracted += moved;
                    if (moved == 0 || handler.getAmountAsLong(index) == 0) {
                        cursor++;
                    }
                }
                if (!simulate) {
                    transaction.commit();
                }
            }
            if (!simulate) {
                cursors.put(key, cursor);
            }
            return extracted;
        }

        @Override
        public long insert(AEKey key, long amount) {
            T resource = resourceOf(key);
            if (resource == null || resource.isEmpty() || amount <= 0) {
                return 0;
            }
            long inserted = 0;
            try (Transaction transaction = Transaction.openRoot()) {
                for (int call = 0; call < MAX_HANDLER_CALLS_PER_OPERATION && inserted < amount; call++) {
                    int request = (int) Math.min(Integer.MAX_VALUE, amount - inserted);
                    int moved = handler.insert(resource, request, transaction);
                    if (moved == 0) {
                        break;
                    }
                    inserted += moved;
                }
                transaction.commit();
            }
            return inserted;
        }
    }

    private static final class ItemSource extends ResourceSource<ItemResource> {
        private ItemSource(ResourceHandler<ItemResource> handler) {
            super(handler);
        }

        @Override
        protected @Nullable AEKey keyOf(ItemResource resource) {
            return AEItemKey.of(resource.toStack());
        }

        @Override
        protected @Nullable ItemResource resourceOf(AEKey key) {
            return key instanceof AEItemKey item ? ItemResource.of(item.toStack()) : null;
        }
    }

    private static final class FluidSource extends ResourceSource<FluidResource> {
        private FluidSource(ResourceHandler<FluidResource> handler) {
            super(handler);
        }

        @Override
        protected @Nullable AEKey keyOf(FluidResource resource) {
            return AEFluidKey.of(resource.toStack(1));
        }

        @Override
        protected @Nullable FluidResource resourceOf(AEKey key) {
            return key instanceof AEFluidKey fluid ? FluidResource.of(fluid.toStack(1)) : null;
        }
    }
}