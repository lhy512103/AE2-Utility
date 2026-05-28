package com.lhy.ae2utility.jei;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.integration.modules.itemlists.CraftingHelper;
import appeng.menu.me.common.GridInventoryEntry;
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

        List<RequestedIngredient> requestedIngredients = collectRequestedIngredients(container, recipeSlots);
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

    private static List<RequestedIngredient> collectRequestedIngredients(MEStorageMenu menu, IRecipeSlotsView recipeSlots) {
        Map<AEKey, Integer> ingredientPriorities = getIngredientPriorities(menu);
        return collectInputSlots(recipeSlots).stream()
                .filter(Ae2TerminalRecipeTransferHandler::hasItemStack)
                .map(slotView -> toRequestedIngredient(ingredientPriorities, slotView))
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

    private static RequestedIngredient toRequestedIngredient(Map<AEKey, Integer> ingredientPriorities, IRecipeSlotView slotView) {
        List<ItemStack> alternatives = chooseRequestedAlternative(ingredientPriorities, slotView);
        int count = Math.max(getDisplayedStack(slotView).getCount(), 1);
        return new RequestedIngredient(alternatives, count);
    }

    private static List<ItemStack> chooseRequestedAlternative(Map<AEKey, Integer> ingredientPriorities, IRecipeSlotView slotView) {
        List<ItemStack> visibleAlternatives = collectVisibleAlternatives(slotView);
        if (visibleAlternatives.isEmpty()) {
            return List.of();
        }
        if (visibleAlternatives.size() == 1) {
            return List.of(visibleAlternatives.getFirst());
        }

        Ingredient ingredient = Ingredient.of(visibleAlternatives.stream().map(ItemStack::copy));
        ItemStack best = chooseBestItem(ingredientPriorities, ingredient, visibleAlternatives);
        return best.isEmpty() ? List.of() : List.of(best);
    }

    private static List<ItemStack> collectVisibleAlternatives(IRecipeSlotView slotView) {
        List<ItemStack> visibleAlternatives = new ArrayList<>();
        ItemStack displayed = getDisplayedStack(slotView);
        if (!displayed.isEmpty()) {
            visibleAlternatives.add(displayed.copy());
        }
        slotView.getItemStacks()
                .filter(stack -> !stack.isEmpty())
                .forEach(stack -> {
                    ItemStack copy = stack.copy();
                    if (!containsEquivalentStack(visibleAlternatives, copy)) {
                        visibleAlternatives.add(copy);
                    }
                });
        return visibleAlternatives;
    }

    private static ItemStack chooseBestItem(Map<AEKey, Integer> ingredientPriorities, Ingredient ingredient,
            List<ItemStack> visibleAlternatives) {
        for (ItemStack visibleAlternative : sortItemAlternatives(ingredientPriorities, visibleAlternatives)) {
            if (ingredient.test(visibleAlternative)) {
                return visibleAlternative.copy();
            }
        }

        ItemStack[] items = ingredient.getItems();
        return items.length > 0 ? items[0].copy() : ItemStack.EMPTY;
    }

    private static List<ItemStack> sortItemAlternatives(Map<AEKey, Integer> ingredientPriorities, List<ItemStack> alternatives) {
        List<ItemStack> sorted = PullIngredientOrdering.preferSpecificComponentsFirst(alternatives);
        if (sorted.size() <= 1) {
            return sorted;
        }

        sorted = new ArrayList<>(sorted);
        sorted.sort(Comparator
                .comparingInt((ItemStack stack) -> getPriority(ingredientPriorities, AEItemKey.of(stack))).reversed()
                .thenComparing(Comparator.comparingInt(PullIngredientOrdering::componentSpecificityRank).reversed())
                .thenComparingLong(ItemStack::hashItemAndComponents));
        return sorted;
    }

    private static Map<AEKey, Integer> getIngredientPriorities(MEStorageMenu menu) {
        if (menu.getClientRepo() == null) {
            return Map.of();
        }

        var orderedEntries = menu.getClientRepo().getAllEntries().stream()
                .sorted(Comparator
                        .comparing(GridInventoryEntry::isCraftable)
                        .thenComparing(Ae2TerminalRecipeTransferHandler::isUndamaged)
                        .thenComparing(GridInventoryEntry::getStoredAmount))
                .map(GridInventoryEntry::getWhat)
                .toList();

        var result = new HashMap<AEKey, Integer>(orderedEntries.size());
        for (int i = 0; i < orderedEntries.size(); i++) {
            var key = orderedEntries.get(i);
            if (key != null) {
                result.put(key, i);
            }
        }

        for (var item : menu.getPlayerInventory().items) {
            var key = AEItemKey.of(item);
            if (key != null) {
                result.putIfAbsent(key, -1);
            }
        }

        return result;
    }

    private static boolean isUndamaged(GridInventoryEntry entry) {
        return !(entry.getWhat() instanceof AEItemKey itemKey) || !itemKey.isDamaged();
    }

    private static int getPriority(Map<AEKey, Integer> priorities, @Nullable AEKey key) {
        return key == null ? Integer.MIN_VALUE : priorities.getOrDefault(key, Integer.MIN_VALUE);
    }

    private static boolean containsEquivalentStack(List<ItemStack> stacks, ItemStack candidate) {
        for (ItemStack existing : stacks) {
            if (ItemStack.isSameItemSameComponents(existing, candidate)) {
                return true;
            }
        }
        return false;
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
