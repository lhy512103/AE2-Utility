package com.lhy.ae2utility.service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.PatternEncodingTermMenu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

import com.lhy.ae2utility.compat.WcwtCompat;

/**
 * 空白样板的扣除与退还。扣除顺序：编码终端空白样板槽 → 玩家背包 → ME 网络；退还时回到原来源。
 * EAEP 等供应器路径下，扣除记录会挂起，直到上传结果确定后由 {@link SequentialUploadResultBridge} 卸下或退还。
 */
public final class BlankPatternLedger {
    public enum Source {
        PATTERN_TERMINAL_SLOT,
        PLAYER_INVENTORY,
        ME_NETWORK
    }

    private record PendingHold(MEStorage inventory, IActionSource actionSource, Source source) {}

    private static final AEItemKey BLANK_PATTERN = AEItemKey.of(AEItems.BLANK_PATTERN);
    private static final ConcurrentHashMap<UUID, PendingHold> EAEP_PENDING = new ConcurrentHashMap<>();

    private BlankPatternLedger() {}

    /** @return 扣除成功时的来源；没有可用空白样板时返回 null */
    static @Nullable Source consumeOne(ServerPlayer player, MEStorage inventory, IActionSource actionSource) {
        AbstractContainerMenu menu = player.containerMenu;
        Iterable<Slot> terminalSlots = terminalBlankSlots(menu);
        if (terminalSlots != null) {
            for (Slot slot : terminalSlots) {
                ItemStack stack = slot.getItem();
                if (AEItems.BLANK_PATTERN.isSameAs(stack) && !stack.isEmpty()) {
                    stack.shrink(1);
                    slot.set(stack.isEmpty() ? ItemStack.EMPTY : stack);
                    slot.setChanged();
                    menu.broadcastChanges();
                    return Source.PATTERN_TERMINAL_SLOT;
                }
            }
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (AEItems.BLANK_PATTERN.isSameAs(stack) && !stack.isEmpty()) {
                stack.shrink(1);
                player.getInventory().setChanged();
                return Source.PLAYER_INVENTORY;
            }
        }

        long extracted = inventory.extract(BLANK_PATTERN, 1, Actionable.MODULATE, actionSource);
        return extracted > 0 ? Source.ME_NETWORK : null;
    }

    static void refund(ServerPlayer player, MEStorage inventory, IActionSource actionSource, Source source) {
        switch (source) {
            case PLAYER_INVENTORY -> ItemHandlerHelper.giveItemToPlayer(player, AEItems.BLANK_PATTERN.stack());
            case ME_NETWORK -> {
                long inserted = inventory.insert(BLANK_PATTERN, 1, Actionable.MODULATE, actionSource);
                if (inserted <= 0) {
                    ItemHandlerHelper.giveItemToPlayer(player, AEItems.BLANK_PATTERN.stack());
                }
            }
            case PATTERN_TERMINAL_SLOT -> {
                if (!returnToTerminalSlot(player.containerMenu)) {
                    ItemHandlerHelper.giveItemToPlayer(player, AEItems.BLANK_PATTERN.stack());
                }
            }
        }
    }

    /** 进入 EAEP「等待选择供应器」路径后挂起本次扣除，等上传结果确定再处理。 */
    static void holdForPendingProvider(ServerPlayer player, MEStorage inventory, IActionSource actionSource,
            Source source) {
        if (player != null) {
            EAEP_PENDING.put(player.getUUID(), new PendingHold(inventory, actionSource, source));
        }
    }

    /** 上传成功或样板已交给玩家：丢弃挂起记录，不退还。 */
    public static void releaseHold(ServerPlayer player) {
        if (player != null) {
            EAEP_PENDING.remove(player.getUUID());
        }
    }

    /** @return 存在挂起记录时退还空白样板并返回 true */
    public static boolean refundHoldIfPending(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        PendingHold hold = EAEP_PENDING.remove(player.getUUID());
        if (hold == null) {
            return false;
        }
        refund(player, hold.inventory(), hold.actionSource(), hold.source());
        return true;
    }

    private static @Nullable Iterable<Slot> terminalBlankSlots(AbstractContainerMenu menu) {
        if (menu instanceof PatternEncodingTermMenu patternMenu) {
            return patternMenu.getSlots(SlotSemantics.BLANK_PATTERN);
        }
        if (WcwtCompat.isWcwtMenu(menu)) {
            return menu.slots;
        }
        return null;
    }

    private static boolean returnToTerminalSlot(AbstractContainerMenu menu) {
        Iterable<Slot> slots = terminalBlankSlots(menu);
        if (slots == null) {
            return false;
        }
        for (Slot slot : slots) {
            ItemStack cur = slot.getItem();
            if (cur.isEmpty()) {
                slot.set(AEItems.BLANK_PATTERN.stack());
            } else if (AEItems.BLANK_PATTERN.isSameAs(cur) && cur.getCount() < cur.getMaxStackSize()) {
                cur.grow(1);
                slot.set(cur);
            } else {
                continue;
            }
            slot.setChanged();
            menu.broadcastChanges();
            return true;
        }
        return false;
    }
}
