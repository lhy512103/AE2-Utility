package com.lhy.ae2utility.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.crafting.PatternDetailsHelper;
import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.network.ClearPatternsPacket;
import com.lhy.ae2utility.client.InventoryPatternUploadQueue;
import com.lhy.ae2utility.client.RecipeTreeUploadQueue;
import com.lhy.ae2utility.jei.ClientRepoCraftableIndex;
import com.lhy.ae2utility.jei.CraftableStateCache;
import com.lhy.ae2utility.jei.JeiClientCacheContext;

@EventBusSubscriber(modid = Ae2UtilityMod.MOD_ID, value = Dist.CLIENT)
public class Ae2UtilityClient {

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        JeiClientCacheContext.advanceClientTick();
        ClientRepoCraftableIndex.advanceClientTick();
        CraftableStateCache.tick();
        InventoryPatternUploadQueue.tick();
        RecipeTreeUploadQueue.tick();
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Ae2UtilityKeyBindings.onKey(event);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide) {
            ItemStack itemStack = event.getItemStack();
            if (!itemStack.isEmpty() && PatternDetailsHelper.isEncodedPattern(itemStack)) {
                // 检查是否同时按下了 Ctrl 和 Shift (Check if Ctrl and Shift are both held)
                if (Screen.hasControlDown() && Screen.hasShiftDown()) {
                    PacketDistributor.sendToServer(ClearPatternsPacket.INSTANCE);
                    event.setCanceled(true);
                }
            }
        }
    }
}
