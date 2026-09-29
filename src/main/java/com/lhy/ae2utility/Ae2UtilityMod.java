package com.lhy.ae2utility;

import com.mojang.logging.LogUtils;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.features.GridLinkables;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import com.lhy.ae2utility.client.Ae2UtilityClientConfig;
import com.lhy.ae2utility.command.Ae2UtilityCommands;
import com.lhy.ae2utility.client.Ae2UtilityClientSetup;
import com.lhy.ae2utility.client.EaepProviderReuse;
import com.lhy.ae2utility.client.EaepProviderScreenLabel;
import com.lhy.ae2utility.client.InventoryPatternUpload;
import com.lhy.ae2utility.client.RemoteEncodeRules;
import com.lhy.ae2utility.init.ModItems;
import com.lhy.ae2utility.init.ModSetup;
import com.lhy.ae2utility.item.MeQuickTransferToolItem;
import com.lhy.ae2utility.jei.CraftableStateCache;
import com.lhy.ae2utility.jei.EncodePatternButtonState;
import com.lhy.ae2utility.jei.JeiClientCacheContext;
import com.lhy.ae2utility.jei.ClientRepoCraftableIndex;
import com.lhy.ae2utility.jei.JeiBatchEncodeQueue;
import com.lhy.ae2utility.jei.JeiEncodeButtonOverlay;
import com.lhy.ae2utility.network.ClearPatternsPacket;
import com.lhy.ae2utility.network.ModNetworking;
import com.lhy.ae2utility.network.SyncEncodeRulesPacket;
import com.lhy.ae2utility.service.EncodeBulkSessionLimiter;
import com.lhy.ae2utility.service.MeQuickTransferService;

@Mod(Ae2UtilityMod.MOD_ID)
public class Ae2UtilityMod {
    public static final String MOD_ID = "ae2utility";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Ae2UtilityMod(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.CLIENT, Ae2UtilityClientConfig.SPEC);
        context.registerConfig(ModConfig.Type.SERVER, Ae2UtilityServerConfig.SPEC);
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> Ae2UtilityClientSetup.init(context.getContainer(), context.getModEventBus()));
        ModSetup.register(context.getModEventBus());
        context.getModEventBus().addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(ModNetworking.class);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        ModNetworking.register();
        event.enqueueWork(() -> GridLinkables.register(ModItems.ME_QUICK_TRANSFER.get(),
                MeQuickTransferToolItem.LINKABLE_HANDLER));
        MeQuickTransferService.registerTickHandler();
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class CommonForgeEvents {
        @SubscribeEvent
        public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                ModNetworking.sendToPlayer(player, SyncEncodeRulesPacket.fromServerConfig());
            }
        }

        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
            Ae2UtilityCommands.register(event);
        }

        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            EncodeBulkSessionLimiter.clearFor(event.getEntity().getUUID());
            MeQuickTransferService.cancel(event.getEntity().getUUID());
        }
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            RemoteEncodeRules.clear();
            CraftableStateCache.clear();
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            JeiClientCacheContext.advanceClientTick();
            ClientRepoCraftableIndex.advanceClientTick();
            CraftableStateCache.tick();
            EncodePatternButtonState.tick();
            JeiBatchEncodeQueue.tick();
        }

        @SubscribeEvent
        public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
            EncodePatternButtonState.clearActiveButton();
        }

        @SubscribeEvent
        public static void onScreenInitPost(ScreenEvent.Init.Post event) {
            EaepProviderReuse.tryAutoChoose(event.getScreen());
        }

        @SubscribeEvent
        public static void onScreenClosing(ScreenEvent.Closing event) {
            if (EaepProviderScreenLabel.isProviderSelectScreen(event.getScreen())) {
                EaepProviderScreenLabel.clear();
            }
        }

        @SubscribeEvent
        public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
            EaepProviderScreenLabel.render(event.getScreen(), event.getGuiGraphics());
            if (!ModList.get().isLoaded("jei")) {
                return;
            }
            JeiEncodeButtonOverlay.render(event.getScreen(), event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        }

        @SubscribeEvent
        public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
            if (event.getButton() != 0) {
                return;
            }
            if (InventoryPatternUpload.onMouseClicked(event.getScreen(), event.getMouseX(), event.getMouseY(),
                    event.getButton())) {
                event.setCanceled(true);
                return;
            }
            if (EncodePatternButtonState.pressIfHovered(event.getMouseX(), event.getMouseY())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
            if (!event.getLevel().isClientSide()) {
                return;
            }
            if (!Screen.hasControlDown() || !Screen.hasShiftDown()) {
                return;
            }

            ItemStack stack = event.getItemStack();
            if (!stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack)) {
                ModNetworking.sendToServer(ClearPatternsPacket.INSTANCE);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }
}
