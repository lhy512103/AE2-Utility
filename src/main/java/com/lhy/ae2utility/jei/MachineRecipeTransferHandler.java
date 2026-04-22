package com.lhy.ae2utility.jei;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.PacketDistributor;

import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.machine.MachineTransferProfile;
import com.lhy.ae2utility.network.MachineRecipeStatePacket;
import com.lhy.ae2utility.network.PullMachineRecipeInputsPacket;
import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;

public class MachineRecipeTransferHandler<C extends AbstractContainerMenu, R> implements IRecipeTransferHandler<C, R> {
    private static final int RED_SLOT_HIGHLIGHT_COLOR = 0x66FF0000;
    private static final int ORANGE_BUTTON_HIGHLIGHT_COLOR = 0x80FFA500;

    private final MachineTransferProfile profile;
    private final IRecipeTransferHandlerHelper transferHelper;

    public MachineRecipeTransferHandler(MachineTransferProfile profile, IRecipeTransferHandlerHelper transferHelper) {
        this.profile = profile;
        this.transferHelper = transferHelper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends C> getContainerClass() {
        return (Class<? extends C>) profile.menuClass();
    }

    @Override
    public Optional<MenuType<C>> getMenuType() {
        return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public RecipeType<R> getRecipeType() {
        return (RecipeType<R>) profile.recipeType();
    }

    @Override
    public IRecipeTransferError transferRecipe(C container, R recipe, IRecipeSlotsView recipeSlots, Player player,
            boolean maxTransfer, boolean doTransfer) {
        List<IRecipeSlotView> inputSlots = collectInputSlots(recipeSlots);
        List<RequestedIngredient> requestedIngredients = collectRequestedIngredients(inputSlots);
        int meaningfulIngredientCount = countMeaningfulIngredients(requestedIngredients);
        if (meaningfulIngredientCount == 0) {
            return transferHelper.createUserErrorWithTooltip(Component.translatable("message.ae2utility.no_item_inputs"));
        }
        if (inputSlots.size() > profile.inputSlotCount()) {
            return transferHelper.createInternalError();
        }

        if (!doTransfer) {
            @Nullable
            MachineRecipeStatePacket packet = MachineRecipeStateCache.getOrRequest(container.containerId, profile.id(), requestedIngredients);
            Preview preview = findTransferPreview(container, player, inputSlots, packet);
            if (preview.anyMissing()) {
                return new MachineTransferError(preview);
            }
            return null;
        }

        PacketDistributor.sendToServer(new PullMachineRecipeInputsPacket(
                container.containerId,
                profile.id(),
                maxTransfer,
                requestedIngredients));
        return null;
    }

    private List<RequestedIngredient> collectRequestedIngredients(List<IRecipeSlotView> inputSlots) {
        return inputSlots.stream()
                .map(this::toRequestedIngredient)
                .toList();
    }

    private Preview findTransferPreview(C container, Player player, List<IRecipeSlotView> inputSlots,
            @Nullable MachineRecipeStatePacket packet) {
        List<IRecipeSlotView> missingSlots = new ArrayList<>();
        var playerStacks = player.getInventory().items;
        var reservedPlayerItems = new int[playerStacks.size()];
        var reservedMachineSlots = new int[profile.inputSlotCount()];
        Map<StackKey, Integer> reservedWireless = new LinkedHashMap<>();

        for (int inputIndex = 0; inputIndex < inputSlots.size(); inputIndex++) {
            IRecipeSlotView slotView = inputSlots.get(inputIndex);
            var ingredient = toIngredient(slotView);
            if (ingredient.isEmpty()) {
                continue;
            }

            int requiredCount = Math.max(getDisplayedStack(slotView).getCount(), 1);
            boolean missing = false;
            for (int i = 0; i < requiredCount; i++) {
                boolean foundLocally = consumeFromMachineInput(container, inputIndex, ingredient, reservedMachineSlots)
                        || consumeFromPlayerInventory(playerStacks, ingredient, reservedPlayerItems);
                boolean found = foundLocally || consumeFromWireless(packet, ingredient, reservedWireless);
                if (!found) {
                    if (packet == null) {
                        // Wireless availability is still pending, don't permanently gray out the JEI transfer button.
                        continue;
                    }
                    missing = true;
                }
            }

            if (missing) {
                missingSlots.add(slotView);
            }
        }

        int meaningfulSlotCount = (int) inputSlots.stream().filter(this::hasItemStack).count();
        boolean anyResolved = meaningfulSlotCount > missingSlots.size();
        MachineRecipeStatePacket.State state = packet != null ? packet.state() : null;
        return new Preview(missingSlots, anyResolved, state);
    }

    private List<IRecipeSlotView> collectInputSlots(IRecipeSlotsView recipeSlots) {
        return recipeSlots.getSlotViews(RecipeIngredientRole.INPUT).stream()
                .toList();
    }

    private int countMeaningfulIngredients(List<RequestedIngredient> requestedIngredients) {
        int count = 0;
        for (RequestedIngredient ingredient : requestedIngredients) {
            if (!ingredient.alternatives().isEmpty() && ingredient.count() > 0) {
                count++;
            }
        }
        return count;
    }


    private boolean consumeFromMachineInput(C container, int recipeInputIndex, Ingredient ingredient, int[] reservedMachineSlots) {
        for (int i = 0; i < profile.inputSlotCount(); i++) {
            var slot = container.getSlot(profile.inputSlotIndex(i));
            var stack = slot.getItem();
            if (stack.isEmpty() || !ingredient.test(stack)) {
                continue;
            }

            int reserved = reservedMachineSlots[i];
            if (stack.getCount() <= reserved) {
                continue;
            }

            reservedMachineSlots[i]++;
            return true;
        }
        return false;
    }

    private boolean consumeFromPlayerInventory(List<ItemStack> playerStacks, Ingredient ingredient, int[] reservedPlayerItems) {
        for (int i = 0; i < playerStacks.size(); i++) {
            ItemStack stack = playerStacks.get(i);
            if (stack.isEmpty() || !ingredient.test(stack)) {
                continue;
            }

            if (stack.getCount() <= reservedPlayerItems[i]) {
                continue;
            }

            reservedPlayerItems[i]++;
            return true;
        }

        return false;
    }

    private boolean consumeFromWireless(@Nullable MachineRecipeStatePacket packet, Ingredient ingredient,
            Map<StackKey, Integer> reservedWireless) {
        if (packet == null || packet.state() != MachineRecipeStatePacket.State.READY) {
            return false;
        }

        for (var entry : packet.availability()) {
            if (!ingredient.test(entry.stack())) {
                continue;
            }

            StackKey key = new StackKey(entry.stack());
            int reserved = reservedWireless.getOrDefault(key, 0);
            if (entry.availableAmount() <= reserved) {
                continue;
            }

            reservedWireless.put(key, reserved + 1);
            return true;
        }

        return false;
    }

    private boolean hasItemStack(IRecipeSlotView slotView) {
        return slotView.getDisplayedItemStack().isPresent() || slotView.getItemStacks().findAny().isPresent();
    }

    private Ingredient toIngredient(IRecipeSlotView slotView) {
        return Ingredient.of(slotView.getItemStacks().map(ItemStack::copy));
    }

    private RequestedIngredient toRequestedIngredient(IRecipeSlotView slotView) {
        if (!hasItemStack(slotView)) {
            return new RequestedIngredient(List.of(), 0);
        }
        List<ItemStack> alternatives = slotView.getItemStacks()
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::copy)
                .distinct()
                .toList();
        int count = Math.max(getDisplayedStack(slotView).getCount(), 1);
        return new RequestedIngredient(alternatives, count);
    }

    private ItemStack getDisplayedStack(IRecipeSlotView slotView) {
        return slotView.getDisplayedItemStack()
                .or(() -> slotView.getItemStacks().findFirst())
                .orElse(ItemStack.EMPTY);
    }

    private record Preview(List<IRecipeSlotView> missingSlots, boolean anyResolved,
            @Nullable MachineRecipeStatePacket.State state) {
        public boolean anyMissing() {
            return !missingSlots.isEmpty();
        }

        public boolean canIgnoreMissing() {
            return anyMissing() && anyResolved;
        }
    }

    private static final class MachineTransferError implements IRecipeTransferError {
        private final Preview preview;

        private MachineTransferError(Preview preview) {
            this.preview = preview;
        }

        @Override
        public Type getType() {
            return preview.canIgnoreMissing() ? Type.COSMETIC : Type.USER_FACING;
        }

        @Override
        public int getButtonHighlightColor() {
            return preview.anyMissing() ? ORANGE_BUTTON_HIGHLIGHT_COLOR : 0;
        }

        @Override
        public void showError(GuiGraphics guiGraphics, int mouseX, int mouseY, IRecipeSlotsView recipeSlotsView, int recipeX, int recipeY) {
            var poseStack = guiGraphics.pose();
            poseStack.pushPose();
            poseStack.translate(recipeX, recipeY, 0);
            for (IRecipeSlotView slot : preview.missingSlots()) {
                slot.drawHighlight(guiGraphics, RED_SLOT_HIGHLIGHT_COLOR);
            }
            poseStack.popPose();
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip) {
            tooltip.add(Component.translatable("message.ae2utility.move_items"));
            tooltip.add(Component.translatable(preview.canIgnoreMissing()
                    ? "message.ae2utility.missing_ignored"
                    : "message.ae2utility.missing_ingredients").withStyle(ChatFormatting.RED));

            if (preview.state() == MachineRecipeStatePacket.State.NO_WIRELESS) {
                tooltip.add(Component.translatable("message.ae2utility.no_wireless_terminal").withStyle(ChatFormatting.GRAY));
            } else if (preview.state() == MachineRecipeStatePacket.State.DISCONNECTED) {
                tooltip.add(Component.translatable("message.ae2utility.wireless_terminal_unavailable").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private record StackKey(ItemStack stack) {
        @Override
        public int hashCode() {
            return ItemStack.hashItemAndComponents(stack);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof StackKey other && ItemStack.isSameItemSameComponents(stack, other.stack);
        }
    }
}
