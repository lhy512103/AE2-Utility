package com.lhy.ae2utility.jei;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import appeng.core.definitions.AEItems;
import appeng.helpers.IPatternTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.SlotSemantics;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.PatternEncodingTermMenu;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.jei.RecipeTreeOpenHelper;
import com.lhy.ae2utility.service.WirelessTerminalContextResolver;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.RecipeTransferPacketHelper;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiUserInput;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;

import net.minecraft.world.inventory.Slot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import mezz.jei.api.gui.drawable.IDrawable;

public class EncodePatternButtonController implements IIconButtonController {
    public static final Map<IRecipeLayoutDrawable<?>, EncodePatternButtonController> CONTROLLERS = new WeakHashMap<>();

    /** JVM 参数 -Dae2utility.debugBlankPattern=true 时每约 2 秒打一条检测诊断到 latest.log */
    private static long lastBlankPatternDebugGameTime = Long.MIN_VALUE;

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

        MEStorageMenu patternLikeMenu = resolveOpenPatternEncodingLikeMenu(player);
        if (patternLikeMenu != null) {
            isAvailable = true;
            // 客户端不可靠判断 ME 是否有空白样板，与「仅背包有终端」分支一致，交给服务端 EncodePatternService
            hasBlankPattern = true;
        } else {
            // 身上有待用的无线/通用终端（未打开 GUI）：客户端无法可靠得知是否已连 ME、库存里是否有空白样板
            isAvailable = inventoryMayEncodeFromWirelessTerminal(player);
            // 与已打开终端时一致：只要显示此按钮，就允许点击，由服务端 EncodePatternService 决定是否真有空白样板
            hasBlankPattern = isAvailable;
        }

        debugLogBlankPatternState(player, patternLikeMenu, isAvailable, hasBlankPattern);

        state.setVisible(isAvailable);
        state.setActive(isAvailable && hasBlankPattern); // We will show missing blank pattern in tooltip if clicked

        // Check pattern state for highlighting
        isOutputCraftable = false;
        isAnyInputCraftable = false;
        
        if (isAvailable) {
            IRecipeSlotsView slotsView = recipeLayout.getRecipeSlotsView();
            List<GenericStack> outputs = RecipeTransferPacketHelper.getEncodingOutputs(recipeLayout.getRecipe(), slotsView);
            for (GenericStack alt : outputs) {
                if (alt != null && CraftableStateCache.isCraftable(alt.what())) {
                    isOutputCraftable = true;
                    break;
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

    private static @Nullable MEStorageMenu resolveOpenPatternEncodingLikeMenu(LocalPlayer player) {
        if (player.containerMenu instanceof PatternEncodingTermMenu pem) {
            return pem;
        }
        if (player.containerMenu instanceof MEStorageMenu me && me.getHost() instanceof IPatternTerminalMenuHost) {
            return me;
        }
        return null;
    }

    /** 主背包 + Curios：AE2/ae2wtlib 无线物品或注册名中含样板/通用终端关键词（兼容其它模组终端）。 */
    private static boolean inventoryMayEncodeFromWirelessTerminal(LocalPlayer player) {
        if (scanSlotsForEncodeTerminal(player.getInventory().getContainerSize(),
                i -> player.getInventory().getItem(i))) {
            return true;
        }
        var curios = player.getCapability(appeng.integration.modules.curios.CuriosIntegration.ITEM_HANDLER);
        if (curios != null) {
            for (int s = 0; s < curios.getSlots(); s++) {
                if (isEncodeTerminalItemStack(curios.getStackInSlot(s))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean scanSlotsForEncodeTerminal(int size, java.util.function.IntFunction<net.minecraft.world.item.ItemStack> stackAt) {
        for (int i = 0; i < size; i++) {
            if (isEncodeTerminalItemStack(stackAt.apply(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEncodeTerminalItemStack(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof WirelessTerminalItem) {
            return true;
        }
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return id.contains("pattern_encoding") || id.contains("universal_terminal");
    }

    private static void debugLogBlankPatternState(LocalPlayer player, @Nullable MEStorageMenu encodingMenu,
            boolean available, boolean hasBlank) {
        if (!Boolean.getBoolean("ae2utility.debugBlankPattern")) {
            return;
        }
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        long gameTime = level.getGameTime();
        if (gameTime - lastBlankPatternDebugGameTime < 40L) {
            return;
        }
        lastBlankPatternDebugGameTime = gameTime;

        var screen = Minecraft.getInstance().screen;
        var sb = new StringBuilder(256);
        sb.append("[ae2utility blankPattern] available=").append(available).append(" hasBlank=").append(hasBlank);
        sb.append(" menu=").append(player.containerMenu.getClass().getName());
        sb.append(" screen=").append(screen != null ? screen.getClass().getName() : "null");
        if (encodingMenu != null) {
            AEItemKey blankKey = AEItemKey.of(AEItems.BLANK_PATTERN);
            sb.append(" link=").append(encodingMenu.getLinkStatus());
            sb.append(" blankKeyVisible=").append(encodingMenu.isKeyVisible(blankKey));
            var repo = encodingMenu.getClientRepo();
            sb.append(" clientRepoNull=").append(repo == null);
            if (repo != null) {
                int total = 0;
                int blankStacks = 0;
                for (GridInventoryEntry e : repo.getAllEntries()) {
                    total++;
                    if (e.getWhat() instanceof AEItemKey ik && AEItems.BLANK_PATTERN.is(ik.getReadOnlyStack())) {
                        blankStacks++;
                    }
                }
                sb.append(" repoEntries=").append(total).append(" repoBlankLike=").append(blankStacks);
            }
            int inBlankSlot = 0;
            for (Slot s : encodingMenu.getSlots(SlotSemantics.BLANK_PATTERN)) {
                if (s.hasItem() && AEItems.BLANK_PATTERN.is(s.getItem())) {
                    inBlankSlot++;
                }
            }
            sb.append(" blankSemanticSlotsWithItem=").append(inBlankSlot);
        } else if (available) {
            var res = WirelessTerminalContextResolver.resolve(player);
            sb.append(" wireless=").append(res.status());
            if (res.host() != null) {
                sb.append(" link=").append(res.host().getLinkStatus());
            }
        }
        Ae2UtilityMod.LOGGER.info(sb.toString());
    }

    @Override
    public void drawExtras(GuiGraphics guiGraphics, Rect2i buttonArea, int mouseX, int mouseY, float partialTick) {
        if (!isAvailable || !hasBlankPattern) {
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
        if (!isAvailable || !hasBlankPattern) {
            return false;
        }

        IRecipeSlotsView slotsView = recipeLayout.getRecipeSlotsView();

        boolean altRecipeTree = Screen.hasAltDown() && !Screen.hasControlDown() && !Screen.hasShiftDown();
        boolean ctrlShiftUpload = JeiBookmarkUtil.isCtrlShiftLeftClickAnchor(input);
        boolean ctrlLeftBookmarkEncode = JeiBookmarkUtil.isCtrlLeftClickAnchor(input);
        if ((altRecipeTree || ctrlShiftUpload || ctrlLeftBookmarkEncode) && input.isSimulate()) {
            return true;
        }

        if (!altRecipeTree && !ctrlShiftUpload && !ctrlLeftBookmarkEncode && input.isSimulate()) {
            return false;
        }

        if (altRecipeTree) {
            RecipeTreeOpenHelper.openFromLayout(recipeLayout, Minecraft.getInstance().screen);
            return true;
        }

        if (ctrlShiftUpload || ctrlLeftBookmarkEncode) {
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
        List<GenericStack> outputs = RecipeTransferPacketHelper.getEncodingOutputs(recipeLayout.getRecipe(), slotsView);

        Object recipe = recipeLayout.getRecipe();
        ResourceLocation recipeId = null;
        if (recipe instanceof RecipeHolder<?> holder) {
            recipeId = holder.id();
        }

        // Ctrl+Shift：强制走 EAEP 的 Shift 上传；Ctrl+左键（不按 Shift）：编码结果进背包，不按 Shift 上传
        boolean shiftDown = Screen.hasShiftDown();
        if (ctrlShiftUpload) {
            shiftDown = true;
        }
        String providerSearchKey = "";

        if (shiftDown && net.neoforged.fml.ModList.get().isLoaded("extendedae_plus")) {
            try {
                // Set the EAEP search key before sending the packet
                Class<?> uploadUtil = Class.forName("com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil");
                if (recipe instanceof RecipeHolder<?> holder && holder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe) {
                    java.lang.reflect.Method preset = uploadUtil.getMethod("presetCraftingProviderSearchKey");
                    preset.invoke(null);
                    java.lang.reflect.Field defaultKey = uploadUtil.getField("DEFAULT_CRAFTING_SEARCH_KEY");
                    providerSearchKey = (String) defaultKey.get(null);
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
                        providerSearchKey = name;
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
                "",
                providerSearchKey,
                providerSearchKey,
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
        boolean detailExpanded = ae2utility$isDetailTooltipExpanded();
        tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_button"));
        if (isAvailable) {
            tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_blue_slots").withStyle(ChatFormatting.BLUE));
            tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_alt_tree").withStyle(ChatFormatting.WHITE));
            if (!detailExpanded) {
                tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_details_prefix")
                        .withStyle(ChatFormatting.WHITE)
                        .append(Component.translatable("jei.tooltip.ae2utility.encode_pattern_details_key")
                                .withStyle(ChatFormatting.AQUA))
                        .append(Component.translatable("jei.tooltip.ae2utility.encode_pattern_details_suffix")
                                .withStyle(ChatFormatting.WHITE)));
            }
        }

        if (isAvailable && detailExpanded) {
            tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_ctrl_left_hint").withStyle(ChatFormatting.WHITE));
        }

        if (isAvailable && detailExpanded && net.neoforged.fml.ModList.get().isLoaded("extendedae_plus")) {
            tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_shift").withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_ctrl_shift_hint").withStyle(ChatFormatting.WHITE));
        }
    }

    private static boolean ae2utility$isDetailTooltipExpanded() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getWindow() == null) {
            return false;
        }
        long window = minecraft.getWindow().getWindow();
        return Screen.hasShiftDown() && InputConstants.isKeyDown(window, GLFW.GLFW_KEY_N);
    }
}
