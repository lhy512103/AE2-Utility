package com.lhy.ae2utility.jei;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.lhy.ae2utility.compat.WcwtCompat;

import net.minecraft.ChatFormatting;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import appeng.menu.me.common.MEStorageMenu;

import appeng.integration.modules.curios.CuriosIntegration;

import com.lhy.ae2utility.client.Ae2UtilityClientConfig;
import com.lhy.ae2utility.service.WirelessEncodeTerminalItems;
import com.lhy.ae2utility.network.RecipeTransferPacketHelper;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiUserInput;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import mezz.jei.api.gui.drawable.IDrawable;

import mezz.jei.common.util.ImmutableRect2i;

import mezz.jei.gui.recipes.RecipesGui;

public class EncodePatternButtonController implements IIconButtonController {
    public static final Map<IRecipeLayoutDrawable<?>, EncodePatternButtonController> CONTROLLERS = new WeakHashMap<>();

    private final IRecipeLayoutDrawable<?> recipeLayout;
    private boolean isAvailable = false;
    private boolean hasBlankPattern = false;

    private int encodeButtonBackdropColor;
    private static final int AE_BLUE_BUTTON_HIGHLIGHT_COLOR = 0x804545FF;
    private static final int AE_ORANGE_BUTTON_HIGHLIGHT_COLOR = 0x80FFA500;
    private static final int AE_BLUE_SLOT_HIGHLIGHT_COLOR = 0x400000FF;
    private static final int AE_RED_SLOT_HIGHLIGHT_COLOR = 0x66FF0000;

    /** {@link mezz.jei.gui.recipes.RecipeLayoutWithButtons#updateBounds} 写入的屏幕像素矩形 */
    private ImmutableRect2i encodeButtonScreenBounds = ImmutableRect2i.EMPTY;

    /**
     * 当前配方行是否在本页可见区域展示（JEI IconButton{@code #isVisible}）。仅在 true 时对 {@link CraftableStateCache}
     * 取样条/箭头底色，避免为滚动区外或其它标签页的实例刷查询。
     */
    private boolean jeiEncodeRowOnVisiblePage = false;
    private boolean cachedRecipeStructureReady = false;
    private List<GenericStack> cachedOutputs = List.of();
    private List<IRecipeSlotView> cachedInputSlots = List.of();
    private Map<IRecipeSlotView, List<mezz.jei.api.ingredients.ITypedIngredient<?>>> cachedInputIngredients = Map.of();
    private Map<IRecipeSlotView, Long> cachedInputCounts = Map.of();
    private Map<IRecipeSlotView, GenericStack> cachedInputRepresentatives = Map.of();
    private long cachedRepresentativeBookmarkSignature = Long.MIN_VALUE;
    private long cachedInputCraftableSignature = Long.MIN_VALUE;
    private Map<IRecipeSlotView, Boolean> cachedInputCraftableStates = Map.of();

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
            // 与 Ae2TerminalRecipeTransferHandler 同款：缺料橙 / 可合成蓝；无 ME 快照时回退 CraftableStateCache
            if (encodeButtonBackdropColor != 0) {
                guiGraphics.fill(xOffset - 1, yOffset - 1, xOffset + 11, yOffset + 11, encodeButtonBackdropColor);
            }

            Icon.ARROW_UP.getBlitter().dest(xOffset, yOffset, 10, 10).blit(guiGraphics);
        }
    };

    public EncodePatternButtonController(IRecipeLayoutDrawable<?> recipeLayout) {
        this.recipeLayout = recipeLayout;
        CONTROLLERS.put(recipeLayout, this);
    }

    /** 由客户端 mixin 在每帧布局更新后同步；按钮隐藏时应传入 {@link ImmutableRect2i#EMPTY}。 */
    public void syncEncodeButtonScreenBounds(ImmutableRect2i absoluteScreenArea) {
        this.encodeButtonScreenBounds = absoluteScreenArea != null ? absoluteScreenArea : ImmutableRect2i.EMPTY;
    }

    /** 与 {@link #syncEncodeButtonScreenBounds} 同帧由 {@code MixinRecipeLayoutWithButtons} 写入。 */
    public void syncJeiEncodeRowOnVisiblePage(boolean onVisiblePage) {
        this.jeiEncodeRowOnVisiblePage = onVisiblePage;
    }

    public boolean isMouseOverEncodeButton(double mouseX, double mouseY) {
        if (!isAvailable || encodeButtonScreenBounds.isEmpty()) {
            return false;
        }
        return encodeButtonScreenBounds.contains(mouseX, mouseY);
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

        boolean mayEncode = playerMayEncodePatterns(player);
        isAvailable = mayEncode;
        // 客户端不可靠判断 ME 是否有空白样板，交给服务端 EncodePatternService
        hasBlankPattern = mayEncode;

        state.setVisible(isAvailable);
        state.setActive(isAvailable && hasBlankPattern); // We will show missing blank pattern in tooltip if clicked

        encodeButtonBackdropColor = 0;
        Minecraft mc = Minecraft.getInstance();

        RecipesGui recipesGui = mc.screen instanceof RecipesGui rg ? rg : null;
        boolean queryCraftableForThisRow = isAvailable && recipesGui != null && jeiEncodeRowOnVisiblePage;

        if (queryCraftableForThisRow) {
            IRecipeSlotsView slotsView = recipeLayout.getRecipeSlotsView();
            List<IRecipeSlotView> inputSlots =
                    slotsView.getSlotViews(mezz.jei.api.recipe.RecipeIngredientRole.INPUT);
            List<GenericStack> outputs = RecipeTransferPacketHelper.getEncodingOutputs(recipeLayout.getRecipe(), slotsView);

            boolean terminalMissing = false;
            boolean terminalInputCraftable = false;
            AbstractContainerMenu parent = recipesGui.getParentContainerMenu();
            if (parent instanceof MEStorageMenu storageMenu && storageMenu.getClientRepo() != null) {
                var preview = TerminalJeRecipeTransferPreview.compute(storageMenu, slotsView);
                terminalMissing = preview.anyMissing();
                terminalInputCraftable = preview.anyCraftable();
            }

            boolean outputHasNetworkPattern = false;
            for (GenericStack alt : outputs) {
                if (alt != null && CraftableStateCache.isCraftable(alt.what())) {
                    outputHasNetworkPattern = true;
                    break;
                }
            }

            /*
             * 编码箭头语义不等同于 AE「拉配方」按钮：JEI 仍能显示缺料黄时，
             * 若产物已在网络 crafting 快照中可走样板（常见于刚上传成功后），应以蓝为先，否则黄会一直压住蓝。
             * 无产物路径时再与拉料预览一致：缺料黄优先于输入可合成蓝。
             */
            if (outputHasNetworkPattern) {
                encodeButtonBackdropColor = AE_BLUE_BUTTON_HIGHLIGHT_COLOR;
            } else if (terminalMissing) {
                encodeButtonBackdropColor = AE_ORANGE_BUTTON_HIGHLIGHT_COLOR;
            } else if (terminalInputCraftable) {
                encodeButtonBackdropColor = AE_BLUE_BUTTON_HIGHLIGHT_COLOR;
            } else {
                for (IRecipeSlotView slotView : inputSlots) {
                    GenericStack one = RecipeTransferPacketHelper.genericStackForCraftableHighlightInputSlot(slotView);
                    if (one != null && one.what() != null && CraftableStateCache.isCraftable(one.what())) {
                        encodeButtonBackdropColor = AE_ORANGE_BUTTON_HIGHLIGHT_COLOR;
                        break;
                    }
                }
            }
        }
    }

    public static boolean playerMayEncodePatterns(LocalPlayer player) {
        return JeiClientCacheContext.getPlayerMayEncodePatterns(player, () -> {
            if (player == null) {
                return false;
            }
            if (resolveOpenPatternEncodingLikeMenu(player) != null) {
                return true;
            }
            if (!Ae2UtilityClientConfig.allowJeiPatternEncodeWithoutOpenTerminal()) {
                return false;
            }
            return inventoryMayEncodeFromWirelessTerminal(player);
        });
    }

    private static @Nullable MEStorageMenu resolveOpenPatternEncodingLikeMenu(LocalPlayer player) {
        if (player.containerMenu instanceof MEStorageMenu me && WcwtCompat.isPatternEncodingLikeMenu(player.containerMenu)) {
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
        var curios = player.getCapability(CuriosIntegration.ITEM_HANDLER);
        if (curios != null) {
            for (int s = 0; s < curios.getSlots(); s++) {
                if (WirelessEncodeTerminalItems.mayProvideWirelessEncoding(curios.getStackInSlot(s))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void drawExtras(GuiGraphics guiGraphics, Rect2i buttonArea, int mouseX, int mouseY, float partialTick) {
        if (!isAvailable || !hasBlankPattern) {
            return;
        }
        // 悬停在编码箭头上时高亮输入槽（与 AE2 拉取配色一致）
        if (mouseX >= buttonArea.getX() && mouseX < buttonArea.getX() + buttonArea.getWidth()
                && mouseY >= buttonArea.getY() && mouseY < buttonArea.getY() + buttonArea.getHeight()) {
            refreshHoveredCraftableState();
            Rect2i recipeRect = this.recipeLayout.getRect();
            var poseStack = guiGraphics.pose();
            poseStack.pushPose();
            poseStack.translate(recipeRect.getX(), recipeRect.getY(), 0);
            drawSlotHighlights(guiGraphics, mouseX, mouseY);
            poseStack.popPose();
        }
    }

    private void refreshHoveredCraftableState() {
        IRecipeSlotsView slotsView = recipeLayout.getRecipeSlotsView();
        analyzeRecipeSlotsIfNeeded(slotsView);
        ensureInputRepresentativesUpToDate();
        ensureInputCraftableStates();
    }

    public void drawSlotHighlights(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (IRecipeSlotView slotView : cachedInputSlots) {
            boolean hasPattern = Boolean.TRUE.equals(cachedInputCraftableStates.get(slotView));
            if (hasPattern) {
                slotView.drawHighlight(guiGraphics, AE_BLUE_SLOT_HIGHLIGHT_COLOR);
            } else if (!slotView.isEmpty()) {
                slotView.drawHighlight(guiGraphics, AE_RED_SLOT_HIGHLIGHT_COLOR);
            }
        }
    }

    private void analyzeRecipeSlotsIfNeeded(IRecipeSlotsView slotsView) {
        if (cachedRecipeStructureReady) {
            return;
        }

        cachedRecipeStructureReady = true;
        cachedRepresentativeBookmarkSignature = Long.MIN_VALUE;
        cachedInputCraftableSignature = Long.MIN_VALUE;
        cachedOutputs = RecipeTransferPacketHelper.getEncodingOutputs(recipeLayout.getRecipe(), slotsView);

        List<IRecipeSlotView> inputSlots = slotsView.getSlotViews(mezz.jei.api.recipe.RecipeIngredientRole.INPUT);
        Map<IRecipeSlotView, List<mezz.jei.api.ingredients.ITypedIngredient<?>>> ingredientMap = new IdentityHashMap<>(inputSlots.size());
        Map<IRecipeSlotView, Long> countMap = new IdentityHashMap<>(inputSlots.size());
        for (IRecipeSlotView slotView : inputSlots) {
            List<mezz.jei.api.ingredients.ITypedIngredient<?>> allIngredients = slotView.getAllIngredients().toList();
            ingredientMap.put(slotView, List.copyOf(allIngredients));
            countMap.put(slotView, RecipeTransferPacketHelper.resolveEncodeSlotDisplayedCount(slotView, allIngredients));
        }

        cachedInputSlots = List.copyOf(inputSlots);
        cachedInputIngredients = ingredientMap;
        cachedInputCounts = countMap;
        cachedInputRepresentatives = Map.of();
        cachedInputCraftableStates = Map.of();
    }

    private void ensureInputRepresentativesUpToDate() {
        long bookmarkSignature = JeiBookmarkKeysCache.getBookmarkSignature();
        if (cachedRepresentativeBookmarkSignature == bookmarkSignature) {
            return;
        }

        List<appeng.api.stacks.AEKey> bookmarkKeys = RecipeTransferPacketHelper.getBookmarkKeys();
        Map<IRecipeSlotView, GenericStack> representatives = new IdentityHashMap<>(cachedInputSlots.size());
        for (IRecipeSlotView slotView : cachedInputSlots) {
            List<mezz.jei.api.ingredients.ITypedIngredient<?>> allIngredients = cachedInputIngredients.get(slotView);
            long count = cachedInputCounts.getOrDefault(slotView, 1L);
            representatives.put(slotView,
                    RecipeTransferPacketHelper.genericStackForCraftableHighlightInputSlot(slotView, allIngredients, bookmarkKeys, count));
        }
        cachedInputRepresentatives = representatives;
        cachedRepresentativeBookmarkSignature = bookmarkSignature;
        cachedInputCraftableSignature = Long.MIN_VALUE;
    }

    private void ensureInputCraftableStates() {
        if (cachedInputCraftableSignature == cachedRepresentativeBookmarkSignature) {
            return;
        }

        Map<IRecipeSlotView, Boolean> states = new IdentityHashMap<>(cachedInputSlots.size());
        for (IRecipeSlotView slotView : cachedInputSlots) {
            GenericStack one = cachedInputRepresentatives.get(slotView);
            boolean craftable = one != null && one.what() != null && CraftableStateCache.isCraftable(one.what());
            states.put(slotView, craftable);
        }
        cachedInputCraftableStates = states;
        cachedInputCraftableSignature = cachedRepresentativeBookmarkSignature;
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

        boolean shiftDown = Screen.hasShiftDown();
        if (ctrlShiftUpload) {
            shiftDown = true;
        }
        final boolean shiftForPacket = shiftDown;

        JeiEncodePacketFactory.tryCreate(recipeLayout, shiftForPacket, false, 0).ifPresent(packet -> {
            PacketDistributor.sendToServer(packet);
            JeiEncodePacketFactory.sendEaepProviderRefreshIfNeeded(shiftForPacket);
        });
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
