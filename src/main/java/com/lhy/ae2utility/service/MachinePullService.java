package com.lhy.ae2utility.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.machine.MachineTransferProfile;
import com.lhy.ae2utility.machine.MachineTransferProfiles;
import com.lhy.ae2utility.network.PullMachineRecipeInputsPacket;
import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;

public final class MachinePullService {
    private static final int MAX_DETAIL_ITEMS = 5;

    private MachinePullService() {
    }

    public static void handle(Player player, PullMachineRecipeInputsPacket payload) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        AbstractContainerMenu menu = serverPlayer.containerMenu;
        var profile = MachineTransferProfiles.byId(payload.profileId());
        if (profile == null || menu.containerId != payload.containerId() || !profile.matches(menu)) {
            return;
        }

        List<RequestedIngredient> requestedIngredients = sanitizeRequestedIngredients(payload.requestedIngredients());
        if (!hasMeaningfulIngredients(requestedIngredients)) {
            serverPlayer.sendSystemMessage(Component.translatable("message.ae2utility.no_item_inputs"));
            return;
        }

        var wireless = WirelessTerminalContextResolver.resolve(serverPlayer);
        var wirelessHost = wireless.host();
        var wirelessStorage = wirelessHost != null ? wirelessHost.getInventory() : null;
        var wirelessEnergy = wirelessHost != null ? resolveEnergySource(wirelessHost) : null;
        var wirelessActionSource = wirelessHost != null ? IActionSource.ofPlayer(serverPlayer, wirelessHost) : IActionSource.ofPlayer(serverPlayer);
        int lockedWirelessSlot = wireless.inventorySlot();

        var playerInventory = serverPlayer.getInventory();
        var playerInventorySnapshot = snapshotInventory(playerInventory.items);
        var reservedPlayerItems = new int[playerInventorySnapshot.size()];
        var machineInputSnapshot = snapshotMachineInputs(menu, profile);
        var reservedMachineItems = new int[machineInputSnapshot.size()];

        int transferSets = payload.maxTransfer()
                ? Math.max(1, computeMaxTransferSets(profile, requestedIngredients, playerInventorySnapshot,
                        lockedWirelessSlot, machineInputSnapshot, wirelessStorage))
                : 1;
        List<RequestedIngredient> scaledIngredients = scaleRequestedIngredients(requestedIngredients, transferSets);

        List<ItemStack> missingItems = new ArrayList<>();
        List<ItemStack> skippedItems = new ArrayList<>();

        for (int ingredientIndex = 0; ingredientIndex < Math.min(scaledIngredients.size(), profile.inputSlotCount()); ingredientIndex++) {
            RequestedIngredient requested = scaledIngredients.get(ingredientIndex);
            int satisfiedByMachine = consumeFromMachineInput(machineInputSnapshot, profile, ingredientIndex,
                    requested.alternatives(), requested.count(), reservedMachineItems);
            int satisfiedByInventory = consumeFromPlayerInventory(playerInventorySnapshot, requested.alternatives(),
                    requested.count() - satisfiedByMachine, reservedPlayerItems, lockedWirelessSlot);
            int movedFromInventory = moveFromPlayerInventoryToMachine(menu, profile, ingredientIndex, playerInventory,
                    requested.alternatives(), satisfiedByInventory, lockedWirelessSlot);
            if (movedFromInventory < satisfiedByInventory) {
                skippedItems.add(getDisplayStack(requested).copyWithCount(satisfiedByInventory - movedFromInventory));
            }

            int missingAmount = requested.count() - satisfiedByMachine - movedFromInventory;
            if (missingAmount <= 0) {
                continue;
            }

            List<ItemStack> extractedStacks = wirelessStorage != null && wirelessEnergy != null
                    ? extractAlternatives(wirelessStorage, wirelessEnergy, wirelessActionSource, requested.alternatives(), missingAmount)
                    : List.of();
            int extractedCount = extractedStacks.stream().mapToInt(ItemStack::getCount).sum();
            if (extractedCount < missingAmount) {
                missingItems.add(getDisplayStack(requested).copyWithCount(missingAmount - extractedCount));
            }

            for (ItemStack extractedStack : extractedStacks) {
                ItemStack remainder = insertIntoMachineSlot(menu, profile, ingredientIndex, extractedStack);
                if (!remainder.isEmpty()) {
                    skippedItems.add(remainder.copy());
                    returnToPlayerOrMe(serverPlayer, playerInventory, wirelessStorage, wirelessEnergy, wirelessActionSource, remainder);
                }
            }
        }

        menu.broadcastChanges();
        sendFeedback(serverPlayer, missingItems, skippedItems);
    }

    private static List<RequestedIngredient> scaleRequestedIngredients(List<RequestedIngredient> requestedIngredients, int transferSets) {
        if (transferSets <= 1) {
            return requestedIngredients;
        }

        List<RequestedIngredient> scaled = new ArrayList<>(requestedIngredients.size());
        for (RequestedIngredient ingredient : requestedIngredients) {
            if (ingredient.count() <= 0 || ingredient.alternatives().isEmpty()) {
                scaled.add(new RequestedIngredient(List.of(), 0));
            } else if (isCatalyst(ingredient)) {
                scaled.add(new RequestedIngredient(ingredient.alternatives(), ingredient.count()));
            } else {
                scaled.add(new RequestedIngredient(ingredient.alternatives(), Math.max(1, Math.multiplyExact(ingredient.count(), transferSets))));
            }
        }
        return scaled;
    }

    private static boolean isCatalyst(RequestedIngredient ingredient) {
        return ingredient.count() == 1 && ingredient.alternatives().stream().anyMatch(s -> s.getMaxStackSize() == 1);
    }

    private static List<RequestedIngredient> sanitizeRequestedIngredients(List<RequestedIngredient> requestedIngredients) {
        List<RequestedIngredient> sanitized = new ArrayList<>();
        for (RequestedIngredient ingredient : requestedIngredients) {
            if (ingredient == null || ingredient.alternatives().isEmpty() || ingredient.count() <= 0) {
                sanitized.add(new RequestedIngredient(List.of(), 0));
                continue;
            }

            List<ItemStack> alternatives = ingredient.alternatives().stream()
                    .filter(stack -> stack != null && !stack.isEmpty())
                    .map(ItemStack::copy)
                    .toList();
            if (alternatives.isEmpty()) {
                sanitized.add(new RequestedIngredient(List.of(), 0));
                continue;
            }

            int count = Math.max(1, ingredient.count());
            sanitized.add(new RequestedIngredient(alternatives, count));
        }
        return sanitized;
    }

    private static boolean hasMeaningfulIngredients(List<RequestedIngredient> requestedIngredients) {
        return countMeaningfulIngredients(requestedIngredients) > 0;
    }

    private static int countMeaningfulIngredients(List<RequestedIngredient> requestedIngredients) {
        int count = 0;
        for (RequestedIngredient ingredient : requestedIngredients) {
            if (ingredient.count() > 0 && !ingredient.alternatives().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static List<ItemStack> snapshotInventory(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    private static List<ItemStack> snapshotMachineInputs(AbstractContainerMenu menu, MachineTransferProfile profile) {
        List<ItemStack> snapshot = new ArrayList<>(profile.inputSlotCount());
        for (int inputSlotIndex : profile.inputSlotIndices()) {
            snapshot.add(menu.getSlot(inputSlotIndex).getItem().copy());
        }
        return snapshot;
    }

    private static int computeMaxTransferSets(MachineTransferProfile profile, List<RequestedIngredient> requestedIngredients,
            List<ItemStack> playerInventorySnapshot, int lockedWirelessSlot, List<ItemStack> machineInputSnapshot,
            @Nullable MEStorage wirelessStorage) {
        Map<AEItemKey, Long> availableByKey = new LinkedHashMap<>();

        for (int i = 0; i < playerInventorySnapshot.size(); i++) {
            if (i == lockedWirelessSlot) {
                continue;
            }
            addStackAmount(availableByKey, playerInventorySnapshot.get(i));
        }
        for (ItemStack stack : machineInputSnapshot) {
            addStackAmount(availableByKey, stack);
        }
        if (wirelessStorage != null) {
            for (var entry : wirelessStorage.getAvailableStacks()) {
                if (entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey itemKey) {
                    availableByKey.merge(itemKey, entry.getLongValue(), Long::sum);
                }
            }
        }

        List<RequestedIngredient> orderedIngredients = new ArrayList<>(requestedIngredients);
        int allowedSetsBySlots = computeSlotCapacitySets(profile, requestedIngredients, machineInputSnapshot);
        int completedSets = 0;
        while (completedSets < Math.min(64, allowedSetsBySlots) && tryReserveSingleSet(availableByKey, orderedIngredients)) {
            completedSets++;
        }
        return completedSets;
    }

    private static int computeSlotCapacitySets(MachineTransferProfile profile, List<RequestedIngredient> requestedIngredients,
            List<ItemStack> machineInputSnapshot) {
        int maxSets = Integer.MAX_VALUE;
        int count = Math.min(profile.inputSlotCount(), requestedIngredients.size());

        for (int i = 0; i < count; i++) {
            ItemStack current = machineInputSnapshot.get(i);
            RequestedIngredient requested = requestedIngredients.get(i);
            if (requested.count() <= 0 || requested.alternatives().isEmpty()) {
                continue;
            }
            if (isCatalyst(requested)) {
                continue;
            }
            int capacity;
            if (current.isEmpty()) {
                capacity = requested.alternatives().stream().mapToInt(ItemStack::getMaxStackSize).max().orElse(64);
            } else if (matchesAnyAlternative(current, requested.alternatives())) {
                capacity = current.getMaxStackSize();
            } else {
                return 0;
            }

            maxSets = Math.min(maxSets, Math.max(0, capacity / requested.count()));
        }

        return maxSets == Integer.MAX_VALUE ? 0 : maxSets;
    }

    private static boolean tryReserveSingleSet(Map<AEItemKey, Long> availableByKey, List<RequestedIngredient> orderedIngredients) {
        Map<AEItemKey, Long> remaining = new LinkedHashMap<>(availableByKey);

        for (RequestedIngredient ingredient : orderedIngredients) {
            if (ingredient.count() <= 0 || ingredient.alternatives().isEmpty()) {
                continue;
            }
            List<AEItemKey> alternatives = ingredient.alternatives().stream()
                    .map(AEItemKey::of)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (alternatives.isEmpty()) {
                return false;
            }

            for (int unit = 0; unit < ingredient.count(); unit++) {
                AEItemKey bestKey = null;
                long bestAmount = 0;

                for (AEItemKey candidate : alternatives) {
                    long available = remaining.getOrDefault(candidate, 0L);
                    if (available > bestAmount) {
                        bestAmount = available;
                        bestKey = candidate;
                    }
                }

                if (bestKey == null || bestAmount <= 0) {
                    return false;
                }

                remaining.put(bestKey, bestAmount - 1);
            }
        }

        availableByKey.clear();
        availableByKey.putAll(remaining);
        return true;
    }

    private static void addStackAmount(Map<AEItemKey, Long> availableByKey, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        var key = AEItemKey.of(stack);
        if (key != null) {
            availableByKey.merge(key, (long) stack.getCount(), Long::sum);
        }
    }

    private static int consumeFromMachineInput(List<ItemStack> machineInputSnapshot, MachineTransferProfile profile,
            int ingredientIndex, List<ItemStack> alternatives, int amount, int[] reservedMachineItems) {
        if (amount <= 0) {
            return 0;
        }

        int consumed = 0;
        for (int i = 0; i < profile.inputSlotCount(); i++) {
            ItemStack stack = machineInputSnapshot.get(i);
            if (stack.isEmpty() || !matchesAnyAlternative(stack, alternatives)) {
                continue;
            }

            int available = stack.getCount() - reservedMachineItems[i];
            if (available <= 0) {
                continue;
            }

            int toConsume = Math.min(available, amount - consumed);
            reservedMachineItems[i] += toConsume;
            consumed += toConsume;
            if (consumed >= amount) {
                break;
            }
        }
        return consumed;
    }

    private static int consumeFromPlayerInventory(List<ItemStack> inventorySnapshot, List<ItemStack> alternatives, int amount,
            int[] reservedPlayerItems, int lockedWirelessSlot) {
        int matched = 0;
        for (int i = 0; i < inventorySnapshot.size(); i++) {
            if (i == lockedWirelessSlot) {
                continue;
            }

            ItemStack stack = inventorySnapshot.get(i);
            if (stack.isEmpty() || !matchesAnyAlternative(stack, alternatives)) {
                continue;
            }

            int available = stack.getCount() - reservedPlayerItems[i];
            if (available <= 0) {
                continue;
            }

            int consumed = Math.min(available, amount - matched);
            reservedPlayerItems[i] += consumed;
            matched += consumed;
            if (matched >= amount) {
                return matched;
            }
        }
        return matched;
    }

    private static int moveFromPlayerInventoryToMachine(AbstractContainerMenu menu, MachineTransferProfile profile,
            int ingredientIndex, Inventory playerInventory, List<ItemStack> alternatives, int amount, int lockedWirelessSlot) {
        if (amount <= 0) {
            return 0;
        }

        int moved = 0;
        for (int i = 0; i < playerInventory.items.size() && moved < amount; i++) {
            if (i == lockedWirelessSlot) {
                continue;
            }

            ItemStack sourceStack = playerInventory.items.get(i);
            if (sourceStack.isEmpty() || !matchesAnyAlternative(sourceStack, alternatives)) {
                continue;
            }

            int moveCount = Math.min(sourceStack.getCount(), amount - moved);
            if (moveCount <= 0) {
                continue;
            }

            ItemStack attempt = sourceStack.copyWithCount(moveCount);
            ItemStack remainder = insertIntoMachineSlot(menu, profile, ingredientIndex, attempt);
            int inserted = moveCount - remainder.getCount();
            if (inserted <= 0) {
                continue;
            }

            sourceStack.shrink(inserted);
            if (sourceStack.isEmpty()) {
                playerInventory.items.set(i, ItemStack.EMPTY);
            }
            moved += inserted;
        }

        if (moved > 0) {
            playerInventory.setChanged();
        }
        return moved;
    }

    private static List<ItemStack> extractAlternatives(MEStorage storage, IEnergySource energy, IActionSource actionSource,
            List<ItemStack> alternatives, int amount) {
        Map<AEItemKey, Integer> extractedByKey = new LinkedHashMap<>();

        for (int i = 0; i < amount; i++) {
            AEItemKey extractedKey = null;
            for (ItemStack alternative : alternatives) {
                var candidate = AEItemKey.of(alternative);
                if (candidate == null) {
                    continue;
                }
                long extracted = StorageHelper.poweredExtraction(energy, storage, candidate, 1, actionSource);
                if (extracted > 0) {
                    extractedKey = candidate;
                    extractedByKey.merge(candidate, 1, Integer::sum);
                    break;
                }
            }
            if (extractedKey == null) {
                break;
            }
        }

        return extractedByKey.entrySet().stream()
                .map(entry -> entry.getKey().toStack(entry.getValue()))
                .toList();
    }

    private static boolean matchesAnyAlternative(ItemStack stack, List<ItemStack> alternatives) {
        for (ItemStack alternative : alternatives) {
            if (ItemStack.isSameItemSameComponents(stack, alternative)) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack getDisplayStack(RequestedIngredient requestedIngredient) {
        return requestedIngredient.alternatives().isEmpty()
                ? ItemStack.EMPTY
                : requestedIngredient.alternatives().getFirst().copyWithCount(requestedIngredient.count());
    }

    private static ItemStack insertIntoMachineSlot(AbstractContainerMenu menu, MachineTransferProfile profile,
            int ingredientIndex, ItemStack stack) {
        if (stack.isEmpty()) {
            return stack.copy();
        }

        ItemStack remainder = stack.copy();
        for (int i = 0; i < profile.inputSlotCount(); i++) {
            int slotIndex = profile.inputSlotIndex(i);
            if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
                continue;
            }

            Slot slot = menu.getSlot(slotIndex);
            remainder = slot.safeInsert(remainder);
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }

        return remainder;
    }


    private static void returnToPlayerOrMe(ServerPlayer player, Inventory inventory, @Nullable MEStorage storage,
            @Nullable IEnergySource energy, IActionSource actionSource, ItemStack remainder) {
        ItemStack remaining = remainder.copy();
        if (!player.getInventory().add(remaining) && storage != null && energy != null) {
            var key = AEItemKey.of(remaining);
            if (key != null) {
                long inserted = StorageHelper.poweredInsert(energy, storage, key, remaining.getCount(), actionSource);
                if (inserted > 0 && inserted < remaining.getCount()) {
                    remaining.shrink((int) inserted);
                } else if (inserted >= remaining.getCount()) {
                    remaining = ItemStack.EMPTY;
                }
            }
        } else {
            remaining = ItemStack.EMPTY;
        }

        if (!remaining.isEmpty()) {
            player.drop(remaining, false);
        }
    }

    private static IEnergySource resolveEnergySource(Object host) {
        if (host instanceof IEnergySource energySource) {
            return energySource;
        }
        if (host instanceof IActionHost actionHost) {
            return (amount, mode, multiplier) -> {
                var node = actionHost.getActionableNode();
                if (node != null && node.isActive()) {
                    return node.getGrid().getEnergyService().extractAEPower(amount, mode, multiplier);
                }
                return 0.0;
            };
        }
        return IEnergySource.empty();
    }

    private static void sendFeedback(ServerPlayer player, List<ItemStack> missingItems, List<ItemStack> skippedItems) {
        if (!missingItems.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.ae2utility.missing", summarizeStacks(missingItems)));
        }

        if (!skippedItems.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.ae2utility.full", summarizeStacks(skippedItems)));
        }
    }

    private static String describeStacks(List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return "[]";
        }
        return "[" + summarizeStacks(stacks) + "]";
    }

    private static String summarizeStacks(List<ItemStack> stacks) {
        Map<String, Integer> summary = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            summary.merge(stack.getHoverName().getString(), stack.getCount(), Integer::sum);
        }

        return summary.entrySet().stream()
                .filter(entry -> !Objects.equals(entry.getKey(), "Air"))
                .limit(MAX_DETAIL_ITEMS)
                .map(entry -> entry.getKey() + " x" + entry.getValue())
                .collect(Collectors.joining(", "));
    }
}
