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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

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

        List<TransferSource> sources = findSources(player.serverLevel(), pos, side);
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

        TransferJob job = new TransferJob(player, storage, entries, actionSource, player.serverLevel(), pos);
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
        } else if (job.player.serverLevel().getGameTime() % 20 == 0) {
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
        IItemHandler items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        if (items == null) {
            items = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        }
        if (items != null) {
            TransferSource itemSource = null;
            if (ModCapabilities.hasSophisticatedCore()) {
                try {
                    IItemHandler unsided = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
                    itemSource = SophisticatedTransferCompat.findSource(level, pos, unsided != null ? unsided : items);
                } catch (LinkageError error) {
                    Ae2UtilityMod.LOGGER.warn("Sophisticated storage fast transfer is unavailable", error);
                }
            }
            sources.add(itemSource != null ? itemSource : new ItemSource(items));
        }

        IFluidHandler fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
        if (fluids == null) {
            fluids = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
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
        ServerLevel linkedLevel = player.server.getLevel(target.dimension());
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
        player.displayClientMessage(Component.translatable(key, args), true);
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

    private static final class ItemSource implements TransferSource {
        private final IItemHandler handler;
        private final Map<AEKey, List<Integer>> slotsByKey = new HashMap<>();
        private final Map<AEKey, Integer> slotCursors = new HashMap<>();

        private ItemSource(IItemHandler handler) {
            this.handler = handler;
        }

        @Override
        public LinkedHashMap<AEKey, Long> available() {
            LinkedHashMap<AEKey, Long> result = new LinkedHashMap<>();
            slotsByKey.clear();
            slotCursors.clear();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                AEItemKey key = AEItemKey.of(stack);
                if (key != null) {
                    result.merge(key, (long) stack.getCount(), Long::sum);
                    slotsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(slot);
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
            int calls = 0;
            List<Integer> slots = slotsByKey.get(key);
            if (slots == null) {
                return 0;
            }
            int nextCursor = slotCursors.getOrDefault(key, 0);
            for (int index = nextCursor; index < slots.size(); index++) {
                if (remaining <= 0) {
                    break;
                }
                int slot = slots.get(index);
                ItemStack existing = handler.getStackInSlot(slot);
                if (!itemKey.matches(existing)) {
                    nextCursor = index + 1;
                    continue;
                }
                if (calls++ >= MAX_HANDLER_CALLS_PER_OPERATION) {
                    break;
                }
                int request = (int) Math.min(Math.min(Integer.MAX_VALUE, remaining), existing.getCount());
                if (request <= 0) {
                    continue;
                }
                ItemStack result = handler.extractItem(slot, request, simulate);
                if (!result.isEmpty() && itemKey.matches(result)) {
                    int count = Math.min(request, result.getCount());
                    extracted += count;
                    remaining -= count;
                }
                if (!simulate) {
                    nextCursor = itemKey.matches(handler.getStackInSlot(slot)) ? index : index + 1;
                }
            }
            if (!simulate) {
                slotCursors.put(key, nextCursor);
            }
            return extracted;
        }

        @Override
        public long insert(AEKey key, long amount) {
            if (!(key instanceof AEItemKey itemKey)) {
                return 0;
            }
            long remaining = amount;
            for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                int request = (int) Math.min(Integer.MAX_VALUE, remaining);
                ItemStack left = handler.insertItem(slot, itemKey.toStack(request), false);
                long inserted = request - left.getCount();
                remaining -= inserted;
            }
            return amount - remaining;
        }
    }

    private static final class FluidSource implements TransferSource {
        private final IFluidHandler handler;

        private FluidSource(IFluidHandler handler) {
            this.handler = handler;
        }

        @Override
        public LinkedHashMap<AEKey, Long> available() {
            LinkedHashMap<AEKey, Long> result = new LinkedHashMap<>();
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack stack = handler.getFluidInTank(tank);
                AEFluidKey key = AEFluidKey.of(stack);
                if (key != null) {
                    result.merge(key, (long) stack.getAmount(), Long::sum);
                }
            }
            return result;
        }

        @Override
        public long extract(AEKey key, long amount, boolean simulate) {
            if (!(key instanceof AEFluidKey fluidKey)) {
                return 0;
            }
            if (simulate) {
                int request = (int) Math.min(Integer.MAX_VALUE, amount);
                FluidStack result = handler.drain(fluidKey.toStack(request), IFluidHandler.FluidAction.SIMULATE);
                return result.isEmpty() || !fluidKey.matches(result) ? 0 : Math.min(request, result.getAmount());
            }

            long remaining = amount;
            long extracted = 0;
            int calls = 0;
            while (remaining > 0 && calls++ < MAX_HANDLER_CALLS_PER_OPERATION) {
                int request = (int) Math.min(Integer.MAX_VALUE, remaining);
                FluidStack result = handler.drain(fluidKey.toStack(request), IFluidHandler.FluidAction.EXECUTE);
                if (result.isEmpty() || !fluidKey.matches(result)) {
                    break;
                }
                extracted += result.getAmount();
                remaining -= result.getAmount();
            }
            return extracted;
        }

        @Override
        public long insert(AEKey key, long amount) {
            if (!(key instanceof AEFluidKey fluidKey)) {
                return 0;
            }
            long remaining = amount;
            int calls = 0;
            while (remaining > 0 && calls++ < MAX_HANDLER_CALLS_PER_OPERATION) {
                int request = (int) Math.min(Integer.MAX_VALUE, remaining);
                int filled = handler.fill(fluidKey.toStack(request), IFluidHandler.FluidAction.EXECUTE);
                if (filled <= 0) {
                    break;
                }
                remaining -= filled;
            }
            return amount - remaining;
        }
    }
}
