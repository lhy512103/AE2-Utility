package com.lhy.ae2utility.jei;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import appeng.menu.me.items.PatternEncodingTermMenu;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.RecipeTransferPacketHelper;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiUserInput;

import java.util.WeakHashMap;
import java.util.Map;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import mezz.jei.api.gui.drawable.IDrawable;

public class EncodePatternButtonController implements IIconButtonController {
    public static final Map<IRecipeLayoutDrawable<?>, EncodePatternButtonController> CONTROLLERS = new WeakHashMap<>();

    private final IRecipeLayoutDrawable<?> recipeLayout;
    private boolean isAvailable = false;
    private boolean hasBlankPattern = false;

    private boolean isOutputCraftable = false;
    private boolean isAnyInputCraftable = false;

    private final IDrawable ARROW_ICON = new IDrawable() {
        @Override
        public int getWidth() { return 10; }
        @Override
        public int getHeight() { return 10; }
        @Override
        public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
            // 注意：JEI 在调用此方法前已经通过 poseStack.translate 移动了坐标，
            // 所以 xOffset/yOffset 始终为 0。背景色填充使用相对坐标即可。
            // 还原为半透明颜色
            if (isOutputCraftable) {
                guiGraphics.fill(xOffset - 1, yOffset - 1, xOffset + 11, yOffset + 11, 0x804545FF); // BLUE_BUTTON_HIGHLIGHT_COLOR
            } else if (isAnyInputCraftable) {
                guiGraphics.fill(xOffset - 1, yOffset - 1, xOffset + 11, yOffset + 11, 0x80FFA500); // ORANGE_BUTTON_HIGHLIGHT_COLOR
            }

            Icon.ARROW_UP.getBlitter().dest(xOffset, yOffset, 10, 10).blit(guiGraphics);
        }
    };

    public EncodePatternButtonController(IRecipeLayoutDrawable<?> recipeLayout) {
        this.recipeLayout = recipeLayout;
        CONTROLLERS.put(recipeLayout, this);
    }

    @Override
    public void initState(IButtonState state) {
        state.setIcon(ARROW_ICON);
    }

    @Override
    public void updateState(IButtonState state) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            state.setVisible(false);
            return;
        }

        // Check if player has Pattern Encoding Terminal open
        if (player.containerMenu instanceof PatternEncodingTermMenu patternMenu) {
            isAvailable = true;
            hasBlankPattern = false;
            // Check terminal slots first
            if (patternMenu.getSlot(98) != null && patternMenu.getSlot(98).hasItem() && patternMenu.getSlot(98).getItem().is(appeng.core.definitions.AEItems.BLANK_PATTERN.asItem())) {
                hasBlankPattern = true;
            }
            if (!hasBlankPattern && patternMenu.getClientRepo() != null) {
                var blankKey = appeng.api.stacks.AEItemKey.of(appeng.core.definitions.AEItems.BLANK_PATTERN);
                for (appeng.menu.me.common.GridInventoryEntry entry : patternMenu.getClientRepo().getAllEntries()) {
                    if (entry.getWhat().equals(blankKey) && entry.getStoredAmount() > 0) {
                        hasBlankPattern = true;
                        break;
                    }
                }
            }
        } else {
            // Check if player has wireless encoding terminal available
            isAvailable = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                String itemName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getInventory().getItem(i).getItem()).toString();
                if (itemName.contains("pattern_encoding") || itemName.contains("universal_terminal")) {
                    isAvailable = true;
                    break;
                }
            }
            
            // Check curios as well
            var curiosItems = player.getCapability(appeng.integration.modules.curios.CuriosIntegration.ITEM_HANDLER);
            if (curiosItems != null && !isAvailable) {
                for (int slot = 0; slot < curiosItems.getSlots(); slot++) {
                    String itemName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(curiosItems.getStackInSlot(slot).getItem()).toString();
                    if (itemName.contains("pattern_encoding") || itemName.contains("universal_terminal")) {
                        isAvailable = true;
                        break;
                    }
                }
            }

            // We can't synchronously check the ME network from a non-terminal GUI.
            // So if they have a wireless terminal, we just optimistically assume they have blank patterns in the network.
            // The server will do the actual check and just fail silently if they don't.
            hasBlankPattern = true;
        }

        state.setVisible(isAvailable);
        state.setActive(isAvailable && hasBlankPattern); // We will show missing blank pattern in tooltip if clicked

        // Check pattern state for highlighting
        isOutputCraftable = false;
        isAnyInputCraftable = false;
        
        if (isAvailable) {
            IRecipeSlotsView slotsView = recipeLayout.getRecipeSlotsView();
            List<List<GenericStack>> outputsLists = RecipeTransferPacketHelper.getGenericStacks(slotsView, mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT);
            for (List<GenericStack> list : outputsLists) {
                if (list != null) {
                    for (GenericStack alt : list) {
                        if (alt != null && CraftableStateCache.isCraftable(alt.what())) {
                            isOutputCraftable = true;
                            break;
                        }
                    }
                }
                if (isOutputCraftable) break;
            }

            if (!isOutputCraftable) {
                List<List<GenericStack>> inputsLists = RecipeTransferPacketHelper.getGenericStacks(slotsView, mezz.jei.api.recipe.RecipeIngredientRole.INPUT);
                for (List<GenericStack> list : inputsLists) {
                    if (list != null) {
                        for (GenericStack alt : list) {
                            if (alt != null && CraftableStateCache.isCraftable(alt.what())) {
                                isAnyInputCraftable = true;
                                break;
                            }
                        }
                    }
                    if (isAnyInputCraftable) break;
                }
            }
        }
    }

    @Override
    public void drawExtras(GuiGraphics guiGraphics, Rect2i buttonArea, int mouseX, int mouseY, float partialTick) {
        if (!isAvailable) {
            return;
        }
        // 悬停在编码箭头上时高亮输入槽（与 AE2 拉取配色一致）
        if (mouseX >= buttonArea.getX() && mouseX < buttonArea.getX() + buttonArea.getWidth()
                && mouseY >= buttonArea.getY() && mouseY < buttonArea.getY() + buttonArea.getHeight()) {
            Rect2i recipeRect = this.recipeLayout.getRect();
            var poseStack = guiGraphics.pose();
            poseStack.pushPose();
            poseStack.translate(recipeRect.getX(), recipeRect.getY(), 0);
            drawSlotHighlights(guiGraphics, mouseX, mouseY);
            poseStack.popPose();
        }
    }

    public void drawSlotHighlights(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        IRecipeSlotsView slotsView = recipeLayout.getRecipeSlotsView();

        for (IRecipeSlotView slotView : slotsView.getSlotViews(mezz.jei.api.recipe.RecipeIngredientRole.INPUT)) {
            boolean hasPattern = false;
            for (mezz.jei.api.ingredients.ITypedIngredient<?> typed : slotView.getAllIngredients().toList()) {
                Object ing = typed.getIngredient();
                appeng.api.stacks.AEKey ingKey = null;
                if (ing instanceof net.minecraft.world.item.ItemStack is && !is.isEmpty()) {
                    ingKey = appeng.api.stacks.AEItemKey.of(is);
                } else if (ing instanceof net.neoforged.neoforge.fluids.FluidStack fs && !fs.isEmpty()) {
                    ingKey = appeng.api.stacks.AEFluidKey.of(fs);
                }

                if (ingKey != null && CraftableStateCache.isCraftable(ingKey)) {
                    hasPattern = true;
                    break;
                }
            }

            if (hasPattern) {
                slotView.drawHighlight(guiGraphics, 1073742079);
            } else if (!slotView.isEmpty()) {
                slotView.drawHighlight(guiGraphics, 1727987712);
            }
        }
    }

    @Override
    public boolean onPress(IJeiUserInput input) {
        if (!isAvailable) {
            return false;
        }

        IRecipeSlotsView slotsView = recipeLayout.getRecipeSlotsView();

        boolean ctrlShiftUpload = JeiBookmarkUtil.isCtrlShiftLeftClickAnchor(input);
        if (ctrlShiftUpload && input.isSimulate()) {
            return true;
        }

        if (!ctrlShiftUpload && input.isSimulate()) {
            return false;
        }

        if (ctrlShiftUpload) {
            int added = JeiBookmarkUtil.bookmarkMissingCraftingInputs(slotsView);
            var player = Minecraft.getInstance().player;
            if (player != null) {
                if (added > 0) {
                    player.displayClientMessage(
                            Component.translatable("message.ae2utility.bookmark_missing_inputs", added).withStyle(ChatFormatting.WHITE),
                            true);
                } else {
                    player.displayClientMessage(
                            Component.translatable("message.ae2utility.bookmark_missing_inputs_none").withStyle(ChatFormatting.WHITE),
                            true);
                }
            }
        }

        List<List<GenericStack>> inputs = RecipeTransferPacketHelper.getGenericStacks(slotsView, mezz.jei.api.recipe.RecipeIngredientRole.INPUT);
        // Note: outputs can still use the first valid stack, so we might need a separate method, or just use the first element of each list.
        // Wait, RecipeTransferPacketHelper.getGenericStacks now returns List<List<GenericStack>>.
        // So we can just use it for outputs too.
        List<GenericStack> outputs = new java.util.ArrayList<>();
        List<List<GenericStack>> outputsLists = RecipeTransferPacketHelper.getGenericStacks(slotsView, mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT);
        for (List<GenericStack> list : outputsLists) {
            if (list == null || list.isEmpty()) outputs.add(null);
            else outputs.add(list.get(0));
        }

        Object recipe = recipeLayout.getRecipe();
        ResourceLocation recipeId = null;
        if (recipe instanceof RecipeHolder<?> holder) {
            recipeId = holder.id();
        }

        boolean shiftDown = Screen.hasShiftDown() || ctrlShiftUpload;

        if (shiftDown && net.neoforged.fml.ModList.get().isLoaded("extendedae_plus")) {
            try {
                // Set the EAEP search key before sending the packet
                Class<?> uploadUtil = Class.forName("com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil");
                if (recipe instanceof RecipeHolder<?> holder && holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe) {
                    java.lang.reflect.Method preset = uploadUtil.getMethod("presetCraftingProviderSearchKey");
                    preset.invoke(null);
                } else {
                    String name = null;
                    if (recipe instanceof RecipeHolder<?> holder) {
                        java.lang.reflect.Method mapRecipe = uploadUtil.getMethod("mapRecipeTypeToSearchKey", net.minecraft.world.item.crafting.Recipe.class);
                        name = (String) mapRecipe.invoke(null, holder.value());
                    } else {
                        java.lang.reflect.Method deriveKey = uploadUtil.getMethod("deriveSearchKeyFromUnknownRecipe", Object.class);
                        name = (String) deriveKey.invoke(null, recipe);
                    }
                    if (name != null && !name.isEmpty()) {
                        java.lang.reflect.Method setName = uploadUtil.getMethod("setLastProcessingName", String.class);
                        setName.invoke(null, name);
                    }
                }
            } catch (Throwable e) {
                // Ignore error silently to clean up logs
            }
        }

        PacketDistributor.sendToServer(new EncodePatternPacket(
                inputs,
                outputs,
                recipeId,
                shiftDown,
                JeiPatternSubstitutionUi.isItemSubstituteOn(),
                JeiPatternSubstitutionUi.isFluidSubstituteOn()));

        if (shiftDown && net.neoforged.fml.ModList.get().isLoaded("extendedae_plus")) {
            try {
                Class<?> packetClass = Class.forName("com.extendedae_plus.network.RequestProvidersListC2SPacket");
                Object instance = packetClass.getDeclaredField("INSTANCE").get(null);
                PacketDistributor.sendToServer((net.minecraft.network.protocol.common.custom.CustomPacketPayload) instance);
            } catch (Exception e) {
                // Ignore error silently to clean up logs
            }
        }
        return true;
    }

    @Override
    public void getTooltips(ITooltipBuilder tooltip) {
        tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_button"));
        if (isAvailable) {
            tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_blue_slots").withStyle(ChatFormatting.BLUE));
        }

        if (net.neoforged.fml.ModList.get().isLoaded("extendedae_plus")) {
            tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_shift").withStyle(ChatFormatting.GRAY));
        }

        if (isAvailable) {
            tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_ctrl_bookmark_hint").withStyle(ChatFormatting.GRAY));
        }

        if (isAvailable && !hasBlankPattern) {
            tooltip.add(Component.translatable("jei.tooltip.ae2utility.missing_blank_pattern").withStyle(net.minecraft.ChatFormatting.RED));
        }
    }
}
