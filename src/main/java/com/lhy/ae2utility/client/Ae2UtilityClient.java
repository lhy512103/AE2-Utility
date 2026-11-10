package com.lhy.ae2utility.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import com.lhy.ae2utility.network.ModNetworking;

import appeng.api.crafting.PatternDetailsHelper;
import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.network.ClearPatternsPacket;

@EventBusSubscriber(modid = Ae2UtilityMod.MOD_ID, value = Dist.CLIENT)
public class Ae2UtilityClient {

    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        ClientCacheLifecycle.tick();
        InventoryPatternUploadQueue.tick();
        SequentialUploadQueue.tick();
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide) {
            ItemStack itemStack = event.getItemStack();
            if (!itemStack.isEmpty() && PatternDetailsHelper.isEncodedPattern(itemStack)) {
                // 检查是否同时按下了 Ctrl 和 Shift (Check if Ctrl and Shift are both held)
                if (Screen.hasControlDown() && Screen.hasShiftDown()) {
                    ModNetworking.sendToServer(ClearPatternsPacket.INSTANCE);
                    event.setCanceled(true);
                }
            }
        }
    }
}
