package com.lhy.ae2utility.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.lhy.ae2utility.init.ModMenus;
import com.lhy.ae2utility.item.RecipeFinderItem;

public class RecipeFinderMenu extends AbstractContainerMenu {
    public static final int SAMPLE_SLOT_INDEX = 0;

    private final Player player;
    private final InteractionHand hand;
    private final SimpleContainer sampleContainer = new SimpleContainer(1);

    public static RecipeFinderMenu fromNetwork(int windowId, Inventory inv, RegistryFriendlyByteBuf buf) {
        boolean mainHand = buf.readBoolean();
        return new RecipeFinderMenu(ModMenus.RECIPE_FINDER.get(), windowId, inv,
                mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    }

    public RecipeFinderMenu(MenuType<?> type, int windowId, Inventory inv, InteractionHand hand) {
        super(type, windowId);
        this.player = inv.player;
        this.hand = hand;

        addSlot(new MarkFilterSlot(sampleContainer, SAMPLE_SLOT_INDEX, 8, 19));

        int invStartY = 212;
        for (int r = 0; r < 3; ++r) {
            for (int c = 0; c < 9; ++c) {
                addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, invStartY + r * 18));
            }
        }
        for (int c = 0; c < 9; ++c) {
            addSlot(new Slot(inv, c, 8 + c * 18, invStartY + 58));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand).getItem() instanceof RecipeFinderItem;
    }

    public ItemStack getSampleStack() {
        return sampleContainer.getItem(SAMPLE_SLOT_INDEX);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == SAMPLE_SLOT_INDEX) {
            if (clickType == ClickType.PICKUP && !getCarried().isEmpty()) {
                applySample(getCarried());
                return;
            }
            if (clickType == ClickType.PICKUP && getCarried().isEmpty() && button == 1) {
                applySample(ItemStack.EMPTY);
                return;
            }
            if (clickType == ClickType.SWAP && button >= 0 && button < Inventory.getSelectionSize()) {
                ItemStack fromHotbar = player.getInventory().getItem(button);
                applySample(fromHotbar);
                return;
            }
        }
        super.clicked(slotId, button, clickType, player);
    }

    public void applySample(ItemStack stack) {
        sampleContainer.setItem(SAMPLE_SLOT_INDEX,
                stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index == SAMPLE_SLOT_INDEX) {
            return ItemStack.EMPTY;
        }
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            itemstack = stack.copy();
            if (!this.moveItemStackTo(stack, 1, this.slots.size(), true)) {
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
    public void removed(Player player) {
        super.removed(player);
    }
}
