package com.lhy.ae2utility.jei;

import java.util.List;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;

import com.lhy.ae2utility.client.RecipeFinderScreen;
import com.lhy.ae2utility.network.RecipeFinderSamplePacket;

public class RecipeFinderGhostHandler implements IGhostIngredientHandler<RecipeFinderScreen> {
    @Override
    public boolean shouldHighlightTargets() {
        return true;
    }

    @Override
    public <I> List<Target<I>> getTargetsTyped(RecipeFinderScreen gui, ITypedIngredient<I> ingredient, boolean doStart) {
        if (ingredient.getItemStack().isEmpty()) {
            return List.of();
        }

        ItemStack ghost = ingredient.getItemStack().get().copyWithCount(1);
        Rect2i area = new Rect2i(gui.getGuiLeft() + 7, gui.getGuiTop() + 18, 20, 20);
        return List.of(new Target<>() {
            @Override
            public Rect2i getArea() {
                return area;
            }

            @Override
            public void accept(I ignored) {
                gui.getMenu().applySample(ghost);
                PacketDistributor.sendToServer(new RecipeFinderSamplePacket(ghost));
            }
        });
    }

    @Override
    public void onComplete() {
    }
}
