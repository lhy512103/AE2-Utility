package com.lhy.ae2utility.jei;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.integration.modules.itemlists.CraftingHelper;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.CraftingTermMenu;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.network.PullRecipeInputsPacket;
import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;
import com.lhy.ae2utility.util.PullIngredientOrdering;

public class Ae2TerminalRecipeTransferHandler<C extends MEStorageMenu> implements IUniversalRecipeTransferHandler<C> {
    /** WCWT 终端继承 {@link CraftingTermMenu}；JEI 若仅按容器类匹配会把「拉配方」处理器误判给它，非合成类配方无法编码。 */
    private static final String WCWT_MENU_CLASS_NAME = "com.lhy.wcwt.menu.WirelessComprehensiveWorkTerminalMenu";

    private final Class<? extends C> containerClass;
    private final Optional<MenuType<C>> menuType;
    private final IRecipeTransferHandlerHelper transferHelper;
    private final Object wcwtDelegateLock = new Object();
    private @Nullable Object wcwtDelegateHandler;
    private @Nullable Method wcwtDelegateTransfer;

    public Ae2TerminalRecipeTransferHandler(Class<? extends C> containerClass, @Nullable MenuType<C> menuType,
            IRecipeTransferHandlerHelper transferHelper) {
        this.containerClass = containerClass;
        this.menuType = Optional.ofNullable(menuType);
        this.transferHelper = transferHelper;
    }

    @Override
    public Class<? extends C> getContainerClass() {
        return containerClass;
    }

    @Override
    public Optional<MenuType<C>> getMenuType() {
        return menuType;
    }

    @Override
    public IRecipeTransferError transferRecipe(C container, Object recipe, IRecipeSlotsView recipeSlots, Player player,
            boolean maxTransfer, boolean doTransfer) {
        if (WCWT_MENU_CLASS_NAME.equals(container.getClass().getName())) {
            return delegateWcwtRecipeTransfer(container, recipe, recipeSlots, player, maxTransfer, doTransfer);
        }

        boolean craftMissing = Screen.hasControlDown();

        if (doTransfer && maxTransfer) {
            var currentScreen = Minecraft.getInstance().screen;
            RecipeTreeOpenHelper.open(recipe, recipeSlots, currentScreen);
            return null;
        }

        if (container instanceof CraftingTermMenu craftingTermMenu
                && recipe instanceof Recipe<?> mcRecipe
                && mcRecipe.getType() == RecipeType.CRAFTING) {
            if (doTransfer) {
                CraftingHelper.performTransfer(craftingTermMenu, null, mcRecipe, craftMissing);
            }
            return null;
        }

        List<RequestedIngredient> requestedIngredients = collectRequestedIngredients(recipeSlots);
        if (requestedIngredients.isEmpty()) {
            return transferHelper.createUserErrorWithTooltip(
                    Component.translatable("message.ae2utility.no_item_inputs"));
        }

        if (!doTransfer) {
            var preview = TerminalJeRecipeTransferPreview.compute(container, recipeSlots);
            if (preview.anyMissingOrCraftable()) {
                return new TerminalTransferError(preview, craftMissing);
            }
            return null;
        }

        PacketDistributor.sendToServer(new PullRecipeInputsPacket(maxTransfer, craftMissing, requestedIngredients));
        return null;
    }

    private IRecipeTransferError delegateWcwtRecipeTransfer(AbstractContainerMenu container, Object recipe,
            IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer) {
        try {
            synchronized (wcwtDelegateLock) {
                if (wcwtDelegateTransfer == null) {
                    Class<?> wcwtMenuClass = Class.forName(WCWT_MENU_CLASS_NAME);
                    Class<?> wcwtHandlerClass = Class.forName("com.lhy.wcwt.compat.jei.WcwtRecipeTransferHandler");
                    wcwtDelegateHandler =
                            wcwtHandlerClass.getConstructor(IRecipeTransferHandlerHelper.class).newInstance(transferHelper);
                    wcwtDelegateTransfer = wcwtHandlerClass.getMethod(
                            "transferRecipe",
                            wcwtMenuClass,
                            Object.class,
                            IRecipeSlotsView.class,
                            Player.class,
                            boolean.class,
                            boolean.class);
                }
            }
            return (IRecipeTransferError) wcwtDelegateTransfer.invoke(
                    wcwtDelegateHandler, container, recipe, recipeSlots, player, maxTransfer, doTransfer);
        } catch (Throwable t) {
            Ae2UtilityMod.LOGGER.warn("WCWT JEI recipe transfer delegation failed", t);
            return transferHelper.createInternalError();
        }
    }

    private static List<RequestedIngredient> collectRequestedIngredients(IRecipeSlotsView recipeSlots) {
        return collectInputSlots(recipeSlots).stream()
                .filter(Ae2TerminalRecipeTransferHandler::hasItemStack)
                .map(Ae2TerminalRecipeTransferHandler::toRequestedIngredient)
                .filter(ingredient -> !ingredient.alternatives().isEmpty())
                .toList();
    }

    private static List<IRecipeSlotView> collectInputSlots(IRecipeSlotsView recipeSlots) {
        return recipeSlots.getSlotViews(RecipeIngredientRole.INPUT).stream()
                .filter(Ae2TerminalRecipeTransferHandler::hasItemStack)
                .toList();
    }

    private static boolean hasItemStack(IRecipeSlotView slotView) {
        return slotView.getDisplayedItemStack().isPresent() || slotView.getItemStacks().findAny().isPresent();
    }

    private static RequestedIngredient toRequestedIngredient(IRecipeSlotView slotView) {
        List<ItemStack> alternatives = PullIngredientOrdering.preferSpecificComponentsFirst(slotView.getItemStacks()
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::copy)
                .distinct()
                .toList());
        int count = Math.max(getDisplayedStack(slotView).getCount(), 1);
        return new RequestedIngredient(alternatives, count);
    }

    private static ItemStack getDisplayedStack(IRecipeSlotView slotView) {
        return slotView.getDisplayedItemStack()
                .or(() -> slotView.getItemStacks().findFirst())
                .orElse(ItemStack.EMPTY);
    }

    private static final class TerminalTransferError implements IRecipeTransferError {
        private static final int RED_SLOT_HIGHLIGHT_COLOR = 0x66FF0000;
        private static final int BLUE_SLOT_HIGHLIGHT_COLOR = 0x400000FF;
        private static final int BLUE_BUTTON_HIGHLIGHT_COLOR = 0x804545FF;
        private static final int ORANGE_BUTTON_HIGHLIGHT_COLOR = 0x80FFA500;

        private final TerminalJeRecipeTransferPreview.PreviewSlots preview;
        private final boolean craftMissing;

        private TerminalTransferError(TerminalJeRecipeTransferPreview.PreviewSlots preview, boolean craftMissing) {
            this.preview = preview;
            this.craftMissing = craftMissing;
        }

        @Override
        public Type getType() {
            return preview.anyCraftable() || preview.canIgnoreMissing() ? Type.COSMETIC : Type.USER_FACING;
        }

        @Override
        public int getButtonHighlightColor() {
            if (preview.anyMissing()) {
                return ORANGE_BUTTON_HIGHLIGHT_COLOR;
            }
            if (preview.anyCraftable()) {
                return BLUE_BUTTON_HIGHLIGHT_COLOR;
            }
            return 0;
        }

        @Override
        public void showError(GuiGraphics guiGraphics, int mouseX, int mouseY, IRecipeSlotsView recipeSlotsView, int recipeX, int recipeY) {
            var poseStack = guiGraphics.pose();
            poseStack.pushPose();
            poseStack.translate(recipeX, recipeY, 0);

            for (IRecipeSlotView slot : preview.missingSlots()) {
                slot.drawHighlight(guiGraphics, RED_SLOT_HIGHLIGHT_COLOR);
            }
            for (IRecipeSlotView slot : preview.craftableSlots()) {
                slot.drawHighlight(guiGraphics, BLUE_SLOT_HIGHLIGHT_COLOR);
            }

            poseStack.popPose();
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip) {
            tooltip.add(Component.translatable("message.ae2utility.move_items"));

            if (preview.anyCraftable()) {
                String key = craftMissing
                        ? "message.ae2utility.will_craft"
                        : "message.ae2utility.ctrl_click_to_craft";
                tooltip.add(Component.translatable(key).withStyle(ChatFormatting.BLUE));
            }

            if (preview.anyMissing()) {
                String key = preview.canIgnoreMissing()
                        ? "message.ae2utility.missing_ignored"
                        : "message.ae2utility.missing_ingredients";
                tooltip.add(Component.translatable(key)
                        .withStyle(ChatFormatting.RED));
            }
        }

        @Override
        public int getMissingCountHint() {
            return preview.totalSize();
        }
    }
}
