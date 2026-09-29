package com.lhy.ae2utility.jei;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;

import net.minecraftforge.fml.ModList;

import appeng.api.stacks.GenericStack;
import appeng.menu.me.common.MEStorageMenu;

import com.lhy.ae2utility.client.Ae2UtilityClientConfig;
import com.lhy.ae2utility.client.EaepProviderScreenLabel;
import com.lhy.ae2utility.compat.JeictCompat;
import com.lhy.ae2utility.compat.WcwtCompat;
import com.lhy.ae2utility.network.ModNetworking;
import com.lhy.ae2utility.network.RecipeTransferPacketHelper;
import com.lhy.ae2utility.service.CuriosWirelessTerminalLookup;
import com.lhy.ae2utility.service.WirelessEncodeTerminalItems;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.elements.DrawableNineSliceTexture;

/**
 * Shared JEI encode-button behavior for the Forge 1.20.1 decorator path.
 */
public final class EncodePatternButtonController {
    private static final int AE_BLUE_BUTTON_HIGHLIGHT_COLOR = 0x804545FF;
    private static final int AE_ORANGE_BUTTON_HIGHLIGHT_COLOR = 0x80FFA500;
    private static final int AE_BLUE_SLOT_HIGHLIGHT_COLOR = 0x400000FF;
    private static final int AE_RED_SLOT_HIGHLIGHT_COLOR = 0x66FF0000;

    private EncodePatternButtonController() {
    }

    public static boolean isAvailable() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && playerMayEncodePatterns(player);
    }

    public static int computeButtonBackdropColor(Object recipe, IRecipeSlotsView slotsView) {
        RecipeAnalysis analysis = analyzeRecipeSlots(recipe, slotsView);

        for (GenericStack alt : analysis.outputs()) {
            if (alt != null && alt.what() != null && CraftableStateCache.isCraftable(alt.what())) {
                return AE_BLUE_BUTTON_HIGHLIGHT_COLOR;
            }
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            MEStorageMenu storageMenu = resolveOpenStorageMenu(player);
            if (storageMenu != null && storageMenu.getClientRepo() != null) {
                var preview = TerminalJeRecipeTransferPreview.compute(storageMenu, slotsView);
                if (preview.anyCraftable()) {
                    return AE_ORANGE_BUTTON_HIGHLIGHT_COLOR;
                }
            }
        }

        for (boolean inputCraftable : computeInputCraftableStates(analysis).values()) {
            if (inputCraftable) {
                return AE_ORANGE_BUTTON_HIGHLIGHT_COLOR;
            }
        }
        return 0;
    }

    public static void drawButton(GuiGraphics guiGraphics, int x, int y, int width, int height, int backdropColor,
            boolean hovered) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        DrawableNineSliceTexture buttonTexture = Internal.getTextures().getButtonForState(false, true, hovered);
        buttonTexture.draw(guiGraphics, x, y, width, height);

        if (backdropColor != 0) {
            guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, backdropColor);
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        int iconSize = 8;
        int iconX = x + (width - iconSize) / 2;
        int iconY = y + (height - iconSize) / 2;
        drawWhiteArrowUp(guiGraphics, iconX, iconY);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawWhiteArrowUp(GuiGraphics guiGraphics, int x, int y) {
        int color = 0xFFFFFFFF;
        guiGraphics.fill(x + 3, y + 1, x + 5, y + 2, color);
        guiGraphics.fill(x + 2, y + 2, x + 6, y + 3, color);
        guiGraphics.fill(x + 1, y + 3, x + 7, y + 4, color);
        guiGraphics.fill(x + 3, y + 4, x + 5, y + 8, color);
    }

    public static void drawSlotHighlights(GuiGraphics guiGraphics, Object recipe, IRecipeSlotsView slotsView) {
        RecipeAnalysis analysis = analyzeRecipeSlots(recipe, slotsView);
        Map<IRecipeSlotView, Boolean> states = computeInputCraftableStates(analysis);
        for (IRecipeSlotView slotView : analysis.inputSlots()) {
            boolean hasPattern = Boolean.TRUE.equals(states.get(slotView));
            if (hasPattern) {
                slotView.drawHighlight(guiGraphics, AE_BLUE_SLOT_HIGHLIGHT_COLOR);
            } else if (!slotView.isEmpty()) {
                slotView.drawHighlight(guiGraphics, AE_RED_SLOT_HIGHLIGHT_COLOR);
            }
        }
    }

    public static List<Component> getTooltipLines() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_button"));
        if (!isAvailable()) {
            return lines;
        }
        lines.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_blue_slots")
                .withStyle(ChatFormatting.BLUE));
        if (Ae2UtilityClientConfig.previewEncodeIngredientsOnArrowHover()) {
            lines.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_hover_preview")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (JeictCompat.isLoaded()) {
            lines.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_alt_jeict")
                    .withStyle(ChatFormatting.WHITE));
        }
        if (!isDetailTooltipExpanded()) {
            lines.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_details_prefix")
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.translatable("jei.tooltip.ae2utility.encode_pattern_details_key")
                            .withStyle(ChatFormatting.AQUA))
                    .append(Component.translatable("jei.tooltip.ae2utility.encode_pattern_details_suffix")
                            .withStyle(ChatFormatting.WHITE)));
            return lines;
        }
        lines.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_ctrl_left_hint")
                .withStyle(ChatFormatting.WHITE));
        if (ModList.get().isLoaded("extendedae_plus")) {
            lines.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_shift")
                    .withStyle(ChatFormatting.WHITE));
            lines.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_ctrl_shift_hint")
                    .withStyle(ChatFormatting.WHITE));
        }
        return lines;
    }

    private static boolean isDetailTooltipExpanded() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return Screen.hasShiftDown() && InputConstants.isKeyDown(window, GLFW.GLFW_KEY_N);
    }

    public static boolean press(IRecipeLayoutDrawable<?> recipeLayout) {
        if (!isAvailable()) {
            return false;
        }
        if (JeictCompat.isLoaded() && Screen.hasAltDown() && !Screen.hasControlDown() && !Screen.hasShiftDown()) {
            return JeictCompat.openFromLayout(recipeLayout, Minecraft.getInstance().screen);
        }
        boolean shiftDown = Screen.hasShiftDown();
        if (Screen.hasControlDown()) {
            bookmarkMissingInputs(recipeLayout);
        }
        var packet = JeiEncodePacketFactory.tryCreate(recipeLayout, shiftDown);
        if (packet.isEmpty() || !packet.get().fitsNetworkLimits()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                String key = packet.isEmpty()
                        ? "message.ae2utility.encode_failed_unsupported"
                        : "message.ae2utility.encode_failed_too_large";
                player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), false);
            }
            return true;
        }
        playClickSound();
        if (shiftDown && ModList.get().isLoaded("extendedae_plus")) {
            EaepProviderScreenLabel.setCurrent(packet.get().patternName(), categoryTitle(recipeLayout));
        }
        ModNetworking.sendToServer(packet.get());
        return true;
    }

    public static String categoryTitle(IRecipeLayoutDrawable<?> recipeLayout) {
        try {
            return recipeLayout.getRecipeCategory().getTitle().getString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static void bookmarkMissingInputs(IRecipeLayoutDrawable<?> recipeLayout) {
        int added = JeiBookmarkUtil.bookmarkMissingCraftingInputs(recipeLayout.getRecipeSlotsView());
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            Component message = added > 0
                    ? Component.translatable("message.ae2utility.bookmark_missing_inputs", added)
                    : Component.translatable("message.ae2utility.bookmark_missing_inputs_none");
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.WHITE), true);
        }
    }

    private static void playClickSound() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public static boolean playerMayEncodePatterns(LocalPlayer player) {
        return JeiClientCacheContext.getPlayerMayEncodePatterns(player, () -> {
            if (player == null) {
                return false;
            }
            if (resolveOpenStorageMenu(player) != null) {
                return true;
            }
            if (!Ae2UtilityClientConfig.allowJeiPatternEncodeWithoutOpenTerminal()) {
                return false;
            }
            return inventoryMayEncodeFromWirelessTerminal(player);
        });
    }

    private static @Nullable MEStorageMenu resolveOpenStorageMenu(LocalPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu instanceof MEStorageMenu me && WcwtCompat.isStorageMenu(menu)) {
            return me;
        }
        return null;
    }

    private static boolean inventoryMayEncodeFromWirelessTerminal(LocalPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (WirelessEncodeTerminalItems.mayProvideWirelessEncoding(player.getInventory().getItem(i))) {
                return true;
            }
        }
        return CuriosWirelessTerminalLookup.hasCandidate(player);
    }

    private static RecipeAnalysis analyzeRecipeSlots(Object recipe, IRecipeSlotsView slotsView) {
        List<GenericStack> outputs = RecipeTransferPacketHelper.getEncodingOutputs(recipe, slotsView);
        List<IRecipeSlotView> inputSlots = List.copyOf(slotsView.getSlotViews(RecipeIngredientRole.INPUT));

        Map<IRecipeSlotView, List<GenericStack>> alternatives = new IdentityHashMap<>(inputSlots.size());
        for (IRecipeSlotView slotView : inputSlots) {
            alternatives.put(slotView, RecipeTransferPacketHelper.collectEncodeAlternativesForInputSlot(slotView));
        }
        return new RecipeAnalysis(outputs, inputSlots, alternatives);
    }

    private static Map<IRecipeSlotView, Boolean> computeInputCraftableStates(RecipeAnalysis analysis) {
        Map<IRecipeSlotView, Boolean> states = new IdentityHashMap<>(analysis.inputSlots().size());
        for (IRecipeSlotView slotView : analysis.inputSlots()) {
            List<GenericStack> alts = analysis.inputAlternatives().get(slotView);
            boolean craftable = false;
            if (alts != null) {
                for (GenericStack gs : alts) {
                    if (gs != null && gs.what() != null && CraftableStateCache.isCraftable(gs.what())) {
                        craftable = true;
                        break;
                    }
                }
            }
            states.put(slotView, craftable);
        }
        return states;
    }

    private record RecipeAnalysis(
            List<GenericStack> outputs,
            List<IRecipeSlotView> inputSlots,
            Map<IRecipeSlotView, List<GenericStack>> inputAlternatives) {
    }
}
