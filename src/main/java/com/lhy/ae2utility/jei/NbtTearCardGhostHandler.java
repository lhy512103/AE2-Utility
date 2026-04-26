package com.lhy.ae2utility.jei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;

import com.lhy.ae2utility.client.NbtTearCardScreen;
import com.lhy.ae2utility.menu.NbtTearCardMenu;
import com.lhy.ae2utility.network.NbtTearGhostSlotPacket;

public class NbtTearCardGhostHandler implements IGhostIngredientHandler<NbtTearCardScreen> {

    /** JEI 在拖拽幽灵物品时会反复查询目标；用于在撕裂卡界面上画单格 AE 蓝高亮 */
    private static volatile boolean filterGhostDragActive;

    public static boolean isFilterGhostDragActive() {
        return filterGhostDragActive;
    }

    public static void clearFilterGhostDragActive() {
        filterGhostDragActive = false;
    }

    @Override
    public boolean shouldHighlightTargets() {
        return false;
    }

    @Override
    public <I> List<Target<I>> getTargetsTyped(NbtTearCardScreen gui, ITypedIngredient<I> ingredient, boolean ignored) {
        if (ingredient.getItemStack().isEmpty()) {
            filterGhostDragActive = false;
            return List.of();
        }
        filterGhostDragActive = true;
        ItemStack ghost = ingredient.getItemStack().get().copy();
        ghost.setCount(1);

        List<Target<I>> targets = new ArrayList<>(NbtTearCardMenu.FILTER_SIZE);
        for (int i = 0; i < NbtTearCardMenu.FILTER_SIZE; i++) {
            Slot slot = gui.getMenu().getSlot(i);
            int x = gui.getGuiLeft() + slot.x;
            int y = gui.getGuiTop() + slot.y;
            var area = new net.minecraft.client.renderer.Rect2i(x, y, 18, 18);
            int slotIndex = i;
            targets.add(new Target<>() {
                @Override
                public net.minecraft.client.renderer.Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(I ignored) {
                    if (!(gui.getMenu() instanceof NbtTearCardMenu)) {
                        return;
                    }
                    PacketDistributor.sendToServer(new NbtTearGhostSlotPacket(slotIndex, ghost.copy()));
                }
            });
        }
        return targets;
    }

    @Override
    public void onComplete() {
        filterGhostDragActive = false;
    }
}
