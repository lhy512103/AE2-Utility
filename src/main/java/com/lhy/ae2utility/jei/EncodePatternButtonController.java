package com.lhy.ae2utility.jei;

import com.lhy.ae2utility.compat.ModCapabilities;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.lhy.ae2utility.compat.JeictCompat;
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
    /**
     * 由 JEI 官方 {@link #drawExtras} 回调在按钮真正被绘制时刷新。
     * 用于在不依赖 mixin 回读 IconButton 的前提下，判断这条 recipe 当前页是否可见。
     */
    private ImmutableRect2i encodeButtonScreenBounds = ImmutableRect2i.EMPTY;
    private int visibleButtonGraceTicks = 0;

    private boolean cachedRecipeStructureReady = false;
    private List<GenericStack> cachedOutputs = List.of();
    private List<IRecipeSlotView> cachedInputSlots = List.of();
    private Map<IRecipeSlotView, List<mezz.jei.api.ingredients.ITypedIngredient<?>>> cachedInputIngredients = Map.of();
    private Map<IRecipeSlotView, Long> cachedInputCounts = Map.of();
    /** 每个输入槽的全部候选（书签命中时为该书签项），用于「任一候选已有样板即高亮」判定，随书签签名刷新。 */
    private Map<IRecipeSlotView, List<GenericStack>> cachedInputAlternatives = Map.of();
    private long cachedRepresentativeBookmarkSignature = Long.MIN_VALUE;
    private long cachedInputCraftableSignature = Long.MIN_VALUE;
    private long cachedInputCraftableCacheVersion = Long.MIN_VALUE;
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

        if (visibleButtonGraceTicks > 0) {
            visibleButtonGraceTicks--;
        } else {
            encodeButtonScreenBounds = ImmutableRect2i.EMPTY;
        }

        encodeButtonBackdropColor = 0;
        Minecraft mc = Minecraft.getInstance();

        RecipesGui recipesGui = mc.screen instanceof RecipesGui rg ? rg : null;
        boolean queryCraftableForThisRow = isAvailable && recipesGui != null && visibleButtonGraceTicks > 0;

        if (queryCraftableForThisRow) {
            IRecipeSlotsView slotsView = recipeLayout.getRecipeSlotsView();
            analyzeRecipeSlotsIfNeeded(slotsView);
            List<IRecipeSlotView> inputSlots = cachedInputSlots;
            List<GenericStack> outputs = cachedOutputs;

            boolean outputHasExistingPattern = false;
            for (GenericStack alt : outputs) {
                if (alt != null && alt.what() != null && CraftableStateCache.isCraftable(alt.what())) {
                    outputHasExistingPattern = true;
                    break;
                }
            }

            /*
             * 编码箭头语义按“已有样板”解释：
             * 蓝色：当前产物已存在对应样板/可 craftable；
             * 橙色：当前产物无样板，但输入里至少有一种材料已有样板；
             * 不高亮：输入与产物都无已有样板。
             */
            if (outputHasExistingPattern) {
                encodeButtonBackdropColor = AE_BLUE_BUTTON_HIGHLIGHT_COLOR;
            } else {
                boolean terminalInputCraftable = false;
                AbstractContainerMenu parent = recipesGui.getParentContainerMenu();
                if (parent instanceof MEStorageMenu storageMenu && storageMenu.getClientRepo() != null) {
                    var preview = TerminalJeRecipeTransferPreview.compute(storageMenu, slotsView);
                    terminalInputCraftable = preview.anyCraftable();
                }

                if (terminalInputCraftable) {
                    encodeButtonBackdropColor = AE_ORANGE_BUTTON_HIGHLIGHT_COLOR;
                } else {
                    /*
                     * 与悬停高亮共用同一份「稳定缓存」状态：每个输入槽只要其候选列表里「任一候选」已有样板即视为满足，
                     * 不再每帧对 JEI 轮换中的单个 displayed 原料查询，从而消除多候选时按钮颜色闪烁。
                     */
                    ensureInputRepresentativesUpToDate();
                    ensureInputCraftableStates();
                    for (IRecipeSlotView slotView : inputSlots) {
                        if (Boolean.TRUE.equals(cachedInputCraftableStates.get(slotView))) {
                            encodeButtonBackdropColor = AE_ORANGE_BUTTON_HIGHLIGHT_COLOR;
                            break;
                        }
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
        encodeButtonScreenBounds = new ImmutableRect2i(
                buttonArea.getX(), buttonArea.getY(), buttonArea.getWidth(), buttonArea.getHeight());
        visibleButtonGraceTicks = 2;

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
        cachedInputAlternatives = Map.of();
        cachedInputCraftableStates = Map.of();
        cachedInputCraftableCacheVersion = Long.MIN_VALUE;
    }

    /** 按书签签名重建每个输入槽的「全部候选」列表（书签命中则仅该项）。 */
    private void ensureInputRepresentativesUpToDate() {
        long bookmarkSignature = JeiBookmarkKeysCache.getBookmarkSignature();
        if (cachedRepresentativeBookmarkSignature == bookmarkSignature) {
            return;
        }

        Map<IRecipeSlotView, List<GenericStack>> alternatives = new IdentityHashMap<>(cachedInputSlots.size());
        for (IRecipeSlotView slotView : cachedInputSlots) {
            alternatives.put(slotView, RecipeTransferPacketHelper.collectEncodeAlternativesForInputSlot(slotView));
        }
        cachedInputAlternatives = alternatives;
        cachedRepresentativeBookmarkSignature = bookmarkSignature;
        cachedInputCraftableSignature = Long.MIN_VALUE;
        cachedInputCraftableCacheVersion = Long.MIN_VALUE;
    }

    /**
     * 每槽判定：候选列表里「任一候选」可合成/已有样板即视为满足。
     * 仅在书签签名或可合成快照版本变化时重算，既不每帧抖动，也能在异步查询回包后刷新。
     */
    private void ensureInputCraftableStates() {
        long bookmarkSig = cachedRepresentativeBookmarkSignature;
        long cacheVer = CraftableStateCache.cacheVersion();
        if (cachedInputCraftableSignature == bookmarkSig && cachedInputCraftableCacheVersion == cacheVer) {
            return;
        }

        Map<IRecipeSlotView, Boolean> states = new IdentityHashMap<>(cachedInputSlots.size());
        for (IRecipeSlotView slotView : cachedInputSlots) {
            List<GenericStack> alts = cachedInputAlternatives.get(slotView);
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
        cachedInputCraftableStates = states;
        cachedInputCraftableSignature = bookmarkSig;
        cachedInputCraftableCacheVersion = cacheVer;
    }

    @Override
    public boolean onPress(IJeiUserInput input) {
        if (!isAvailable || !hasBlankPattern) {
            return false;
        }

        IRecipeSlotsView slotsView = recipeLayout.getRecipeSlotsView();

        boolean altRecipeTree = JeictCompat.isLoaded()
                && Screen.hasAltDown() && !Screen.hasControlDown() && !Screen.hasShiftDown();
        boolean ctrlShiftUpload = JeiBookmarkUtil.isCtrlShiftLeftClickAnchor(input);
        boolean ctrlLeftBookmarkEncode = JeiBookmarkUtil.isCtrlLeftClickAnchor(input);
        if ((altRecipeTree || ctrlShiftUpload || ctrlLeftBookmarkEncode) && input.isSimulate()) {
            return true;
        }

        if (!altRecipeTree && !ctrlShiftUpload && !ctrlLeftBookmarkEncode && input.isSimulate()) {
            return false;
        }

        if (altRecipeTree) {
            JeictCompat.openFromLayout(recipeLayout, Minecraft.getInstance().screen);
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
        });
        return true;
    }

    @Override
    public void getTooltips(ITooltipBuilder tooltip) {
        boolean detailExpanded = ae2utility$isDetailTooltipExpanded();
        tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_button"));
        if (isAvailable) {
            tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_blue_slots").withStyle(ChatFormatting.BLUE));
            if (JeictCompat.isLoaded()) {
                tooltip.add(Component.translatable("jei.tooltip.ae2utility.encode_pattern_alt_tree").withStyle(ChatFormatting.WHITE));
            }
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

        if (isAvailable && detailExpanded && ModCapabilities.hasExtendedAePlus()) {
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
