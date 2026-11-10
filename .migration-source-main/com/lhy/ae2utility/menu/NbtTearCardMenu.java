package com.lhy.ae2utility.menu;

import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.RegistryFriendlyByteBuf;

import com.lhy.ae2utility.card.NbtTearFilter;
import com.lhy.ae2utility.init.ModDataComponents;
import com.lhy.ae2utility.init.ModMenus;
import com.lhy.ae2utility.item.NbtTearCardItem;

public class NbtTearCardMenu extends AbstractContainerMenu {
    /** 与潜影盒上半部分一致：3×9 标记格 */
    public static final int FILTER_SIZE = 27;
    private final Player player;
    private final InteractionHand hand;
    private final SimpleContainer filterSlots = new SimpleContainer(FILTER_SIZE);

    public static NbtTearCardMenu fromNetwork(int windowId, Inventory inv, RegistryFriendlyByteBuf buf) {
        boolean mainHand = buf.readBoolean();
        return new NbtTearCardMenu(ModMenus.NBT_TEAR_CARD.get(), windowId, inv,
                mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    }

    public NbtTearCardMenu(MenuType<?> type, int windowId, Inventory inv, InteractionHand hand) {
        super(type, windowId);
        this.player = inv.player;
        this.hand = hand;
        loadFromCard(inv.player.getItemInHand(hand));

        int startX = 8;
        int startY = 18;
        for (int i = 0; i < FILTER_SIZE; i++) {
            int col = i % 9;
            int row = i / 9;
            this.addSlot(new MarkFilterSlot(filterSlots, i, startX + col * 18, startY + row * 18));
        }

        int invY = 84;
        for (int r = 0; r < 3; ++r) {
            for (int c = 0; c < 9; ++c) {
                this.addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, invY + r * 18));
            }
        }
        for (int c = 0; c < 9; ++c) {
            this.addSlot(new Slot(inv, c, 8 + c * 18, invY + 58));
        }
    }

    private void loadFromCard(ItemStack card) {
        filterSlots.clearContent();
        if (!(card.getItem() instanceof NbtTearCardItem)) {
            return;
        }
        NbtTearFilter filter = card.getOrDefault(ModDataComponents.NBT_TEAR_FILTER, NbtTearFilter.DEFAULT);
        int i = 0;
        for (var id : filter.itemIds()) {
            if (i >= FILTER_SIZE) {
                break;
            }
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
            if (item != null && !item.equals(net.minecraft.world.item.Items.AIR)) {
                filterSlots.setItem(i, new ItemStack(item, 1));
                i++;
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand).getItem() instanceof NbtTearCardItem;
    }

    @Override
    public void removed(Player player) {
        if (!player.level().isClientSide() && player.getItemInHand(hand).getItem() instanceof NbtTearCardItem) {
            java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
            for (int i = 0; i < FILTER_SIZE; i++) {
                stacks.add(filterSlots.getItem(i).copy());
            }
            player.getItemInHand(hand).set(ModDataComponents.NBT_TEAR_FILTER, NbtTearFilter.fromItemStacks(stacks));
            // 标记格内的物品仅为配置用显示，关闭界面时不得当真实物品退进背包（避免“刷物”）
            for (int i = 0; i < FILTER_SIZE; i++) {
                filterSlots.setItem(i, ItemStack.EMPTY);
            }
        }
        super.removed(player);
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (!player.level().isClientSide() && player.getItemInHand(hand).getItem() instanceof NbtTearCardItem) {
            java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
            for (int i = 0; i < FILTER_SIZE; i++) {
                stacks.add(filterSlots.getItem(i).copy());
            }
            player.getItemInHand(hand).set(ModDataComponents.NBT_TEAR_FILTER, NbtTearFilter.fromItemStacks(stacks));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= 0 && index < FILTER_SIZE) {
            return ItemStack.EMPTY;
        }
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            itemstack = stack.copy();
            if (!this.moveItemStackTo(stack, FILTER_SIZE, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return itemstack;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < FILTER_SIZE) {
            if (clickType == ClickType.PICKUP && !getCarried().isEmpty()) {
                applyGhostFilterSlot(slotId, getCarried());
                return;
            }
            if (clickType == ClickType.PICKUP && getCarried().isEmpty() && button == 1) {
                filterSlots.setItem(slotId, ItemStack.EMPTY);
                broadcastChanges();
                return;
            }
            if (clickType == ClickType.SWAP && button >= 0 && button < Inventory.getSelectionSize()) {
                ItemStack fromHotbar = player.getInventory().getItem(button);
                if (!fromHotbar.isEmpty()) {
                    applyGhostFilterSlot(slotId, fromHotbar);
                }
                return;
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    /**
     * Server-only: apply a JEI ghost drag (or equivalent) to one filter slot. Whitelist uses item id only.
     */
    public void applyGhostFilterSlot(int slotId, ItemStack stack) {
        if (!stillValid(player) || slotId < 0 || slotId >= FILTER_SIZE) {
            return;
        }
        ItemStack toSet;
        if (stack == null || stack.isEmpty()) {
            toSet = ItemStack.EMPTY;
        } else {
            toSet = new ItemStack(stack.getItem(), 1);
        }
        filterSlots.setItem(slotId, toSet);
        slotsChanged(filterSlots);
    }
}
