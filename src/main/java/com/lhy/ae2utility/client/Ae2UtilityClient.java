package com.lhy.ae2utility.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.crafting.PatternDetailsHelper;
import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.network.ClearPatternsPacket;
import com.lhy.ae2utility.jei.CraftableStateCache;
import com.lhy.ae2utility.jei.JeiPatternSubstitutionUi;

import mezz.jei.gui.recipes.RecipesGui;

@EventBusSubscriber(modid = Ae2UtilityMod.MOD_ID, value = Dist.CLIENT)
public class Ae2UtilityClient {

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        CraftableStateCache.tick();
        InventoryPatternUploadQueue.tick();
        RecipeTreeUploadQueue.tick();
    }

    @SubscribeEvent
    public static void onMouseClick(net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof RecipesGui && JeiPatternSubstitutionUi.handleClick(event.getMouseX(), event.getMouseY())) {
            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            event.setCanceled(true);
        }
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
