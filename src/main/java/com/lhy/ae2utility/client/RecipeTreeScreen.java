package com.lhy.ae2utility.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.lhy.ae2utility.client.recipe_tree.RecipeTreeExpansionResolver;
import com.lhy.ae2utility.client.recipe_tree.RecipeTreeInputViewModel;
import com.lhy.ae2utility.client.recipe_tree.RecipeTreeInputViewModel.DisplayOption;
import com.lhy.ae2utility.client.recipe_tree.RecipeTreeNodeViewModel;
import com.lhy.ae2utility.client.recipe_tree.RecipeTreeRecipeViewModel;
import com.lhy.ae2utility.client.recipe_tree.RecipeTreeRootContext;
import com.lhy.ae2utility.jei.Ae2UtilityJeiPlugin;
import com.lhy.ae2utility.jei.CraftableStateCache;
import com.lhy.ae2utility.jei.JeiPatternSubstitutionUi;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.PullRecipeInputsPacket;
import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;
import com.lhy.ae2utility.util.GenericIngredientUtil;

import appeng.api.stacks.GenericStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;

public class RecipeTreeScreen extends Screen {
    private static final int PANEL_WIDTH = 344;
    private static final int PANEL_HEIGHT = 208;
    private static final int HEADER_HEIGHT = 34;
    private static final int NODE_HEIGHT = 30;
    private static final int INPUT_HEIGHT = 20;
    private static final int INDENT = 14;
    private static final int SCROLL_STEP = 12;
    private static final int VIEW_TOP = HEADER_HEIGHT + 4;
    private static final int VIEW_BOTTOM_PAD = 28;
    private static final int SELECTION_PANEL_WIDTH = 154;
    private static final int SELECTION_CARD_HEIGHT = 78;
    private static final int SELECTION_CARD_GAP = 6;

    private final RecipeTreeRootContext context;
    private final Screen returnScreen;

    private int leftPos;
    private int topPos;
    private int scrollOffset;
    private int contentHeight;
    private int selectionScroll;
    private int selectionContentHeight;
    private int selectionGroupIndex;
    private int alternativeScroll;

    private final List<RowLayout> rows = new ArrayList<>();
    private final List<Button> rowButtons = new ArrayList<>();
    private final List<CandidateCardBounds> candidateCards = new ArrayList<>();
    private final List<CandidateSlotBounds> candidateSlots = new ArrayList<>();
    private final List<AlternativeOptionBounds> alternativeOptionBounds = new ArrayList<>();
    private Button backButton;
    private Button encodeButton;
    private Button applyButton;
    private Button overviewButton;
    private Button cancelSelectionButton;
    private Button prevGroupButton;
    private Button nextGroupButton;

    private @Nullable PendingSelection pendingSelection;
    private @Nullable PendingAlternativeSelection pendingAlternativeSelection;

    public RecipeTreeScreen(RecipeTreeRootContext context) {
        super(Component.translatable("gui.ae2utility.recipe_tree.title"));
        this.context = context;
        this.returnScreen = context.returnScreen() != null ? context.returnScreen() : new PlaceholderReturnScreen();
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - PANEL_WIDTH) / 2;
        this.topPos = (this.height - PANEL_HEIGHT) / 2;

        this.backButton = Button.builder(Component.translatable("gui.ae2utility.recipe_tree.back"),
                btn -> this.onClose()).bounds(leftPos + 8, topPos + PANEL_HEIGHT - 22, 60, 18).build();
        this.overviewButton = Button.builder(Component.translatable("gui.ae2utility.recipe_tree.overview"),
                btn -> this.minecraft.setScreen(new RecipeTreeOverviewScreen(context, this))).bounds(leftPos + PANEL_WIDTH - 54, topPos + 8, 42, 18).build();
        this.encodeButton = Button.builder(Component.translatable("gui.ae2utility.recipe_tree.encode"),
                btn -> this.encodePatterns()).bounds(leftPos + PANEL_WIDTH / 2 - 30, topPos + PANEL_HEIGHT - 22, 60, 18).build();
        this.applyButton = Button.builder(Component.translatable("gui.ae2utility.recipe_tree.upload"),
                btn -> this.uploadPatterns()).bounds(leftPos + PANEL_WIDTH - 68, topPos + PANEL_HEIGHT - 22, 60, 18).build();
        this.cancelSelectionButton = Button.builder(Component.translatable("gui.ae2utility.recipe_tree.cancel"),
                btn -> closeSelection()).bounds(0, 0, 54, 18).build();
        this.prevGroupButton = Button.builder(Component.literal("<"), btn -> switchSelectionGroup(-1))
                .bounds(0, 0, 18, 18).build();
        this.nextGroupButton = Button.builder(Component.literal(">"), btn -> switchSelectionGroup(1))
                .bounds(0, 0, 18, 18).build();

        this.addRenderableWidget(backButton);
        this.addRenderableWidget(overviewButton);
        this.addRenderableWidget(encodeButton);
        this.addRenderableWidget(applyButton);
        this.addRenderableWidget(cancelSelectionButton);
        this.addRenderableWidget(prevGroupButton);
        this.addRenderableWidget(nextGroupButton);
        rebuildRows();
    }

    private void rebuildRows() {
        for (Button button : rowButtons) {
            this.removeWidget(button);
        }
        rowButtons.clear();
        rows.clear();

        int y = 0;
        y = collectRows(context.root(), 0, y);
        this.contentHeight = y;
        clampScroll();
        refreshRowButtons();
        updateSelectionButtons();
    }

    private int collectRows(RecipeTreeNodeViewModel node, int depth, int y) {
        rows.add(new RowLayout(RowType.NODE, node, null, null, null, null, depth, y, NODE_HEIGHT));
        y += NODE_HEIGHT;
        for (InputGroup inputGroup : groupInputs(node)) {
            autoApplyRememberedChild(node, inputGroup);
            Button altButton = null;
            if (inputGroup.hasAlternativeChoices()) {
                altButton = Button.builder(Component.literal("v"), btn -> openAlternativeSelection(inputGroup))
                        .bounds(0, 0, 16, 16)
                        .build();
            }
            Button clearButton = null;
            if (inputGroup.child() != null) {
                clearButton = Button.builder(Component.literal("-"), btn -> clearChildSelection(inputGroup))
                        .bounds(0, 0, 16, 16)
                        .build();
            }
            Button plusButton = Button.builder(Component.literal("+"), btn -> openSelection(node, inputGroup))
                    .bounds(0, 0, 16, 16)
                    .build();
            rows.add(new RowLayout(RowType.INPUT, node, inputGroup, altButton, clearButton, plusButton, depth + 1, y, INPUT_HEIGHT));
            y += INPUT_HEIGHT;
            if (inputGroup.child() != null) {
                y = collectRows(inputGroup.child(), depth + 2, y);
            }
        }
        return y;
    }

    private void refreshRowButtons() {
        int viewTop = topPos + VIEW_TOP;
        int viewBottom = topPos + PANEL_HEIGHT - VIEW_BOTTOM_PAD;
        for (RowLayout row : rows) {
            int absoluteY = viewTop + row.relativeY() - scrollOffset;
            if (absoluteY + row.height() < viewTop || absoluteY > viewBottom) {
                continue;
            }
            int nextX = getTreeRight() - 18;
            if (row.plusButton() != null) {
                row.plusButton().active = row.inputGroup() == null || !hasExistingPatternForInput(row.inputGroup());
                row.plusButton().setX(nextX);
                row.plusButton().setY(absoluteY + 2);
                this.addRenderableWidget(row.plusButton());
                rowButtons.add(row.plusButton());
                nextX -= 18;
            }
            if (row.clearButton() != null) {
                row.clearButton().setX(nextX);
                row.clearButton().setY(absoluteY + 2);
                this.addRenderableWidget(row.clearButton());
                rowButtons.add(row.clearButton());
                nextX -= 18;
            }
            if (row.altButton() != null) {
                row.altButton().setX(nextX);
                row.altButton().setY(absoluteY + 2);
                this.addRenderableWidget(row.altButton());
                rowButtons.add(row.altButton());
            }
        }
    }

    private void openAlternativeSelection(InputGroup inputGroup) {
        pendingAlternativeSelection = new PendingAlternativeSelection(inputGroup);
        alternativeScroll = 0;
    }

    private void clearChildSelection(InputGroup inputGroup) {
        context.forgetSelection(signatureOf(inputGroup.representative()));
        for (RecipeTreeInputViewModel member : inputGroup.members()) {
            member.setChild(null);
        }
        rebuildRows();
    }

    private void openSelection(RecipeTreeNodeViewModel parent, InputGroup inputGroup) {
        List<RecipeTreeRecipeViewModel> candidates = RecipeTreeExpansionResolver.resolveCandidates(inputGroup.representative()).stream()
                .filter(candidate -> !parent.containsRecipe(candidate))
                .toList();
        this.pendingSelection = new PendingSelection(parent, inputGroup, candidates, groupCandidates(candidates));
        this.selectionScroll = 0;
        this.selectionGroupIndex = 0;
        clampSelectionScroll();
        rebuildRows();
    }

    private void closeSelection() {
        this.pendingSelection = null;
        this.pendingAlternativeSelection = null;
        this.selectionScroll = 0;
        this.selectionContentHeight = 0;
        this.selectionGroupIndex = 0;
        this.alternativeScroll = 0;
        this.candidateCards.clear();
        this.candidateSlots.clear();
        this.alternativeOptionBounds.clear();
        updateSelectionButtons();
        rebuildRows();
    }

    private void updateSelectionButtons() {
        boolean visible = pendingSelection != null;
        cancelSelectionButton.visible = visible;
        cancelSelectionButton.active = visible;
        encodeButton.visible = !visible;
        encodeButton.active = !visible;
        applyButton.visible = !visible;
        applyButton.active = !visible && net.neoforged.fml.ModList.get().isLoaded("extendedae_plus");
        overviewButton.visible = !visible;
        overviewButton.active = !visible;
        overviewButton.setX(getTreeRight() - 46);
        overviewButton.setY(topPos + 8);
        prevGroupButton.visible = visible && pendingSelection != null && pendingSelection.groups().size() > 1;
        prevGroupButton.active = prevGroupButton.visible && selectionGroupIndex > 0;
        nextGroupButton.visible = visible && pendingSelection != null && pendingSelection.groups().size() > 1;
        nextGroupButton.active = nextGroupButton.visible && selectionGroupIndex < pendingSelection.groups().size() - 1;
        if (visible) {
            cancelSelectionButton.setX(getSelectionPanelX() + SELECTION_PANEL_WIDTH - 61);
            cancelSelectionButton.setY(topPos + PANEL_HEIGHT - 31);
            prevGroupButton.setX(getSelectionPanelX() + 6);
            prevGroupButton.setY(topPos + 32);
            nextGroupButton.setX(getSelectionPanelX() + SELECTION_PANEL_WIDTH - 24);
            nextGroupButton.setY(topPos + 32);
        }
    }

    private void switchSelectionGroup(int delta) {
        if (pendingSelection == null) {
            return;
        }
        selectionGroupIndex = Math.max(0, Math.min(pendingSelection.groups().size() - 1, selectionGroupIndex + delta));
        selectionScroll = 0;
        clampSelectionScroll();
        updateSelectionButtons();
    }

    private void selectCandidate(RecipeTreeRecipeViewModel selected) {
        if (pendingSelection == null) {
            return;
        }
        RecipeTreeNodeViewModel childNode = new RecipeTreeNodeViewModel(selected, pendingSelection.parent());
        for (RecipeTreeInputViewModel input : pendingSelection.inputGroup().members()) {
            input.setChild(childNode);
        }
        applyChildRecipeReuse(pendingSelection.parent(), pendingSelection.inputGroup(), selected);
        closeSelection();
    }

    private void uploadPatterns() {
        List<EncodePatternPacket> packets = collectEncodablePackets(true);
        if (packets.isEmpty()) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.ae2utility.recipe_tree_no_patterns"), true);
            }
            return;
        }
        RecipeTreeUploadQueue.start(packets);
        this.onClose();
    }

    private void encodePatterns() {
        List<EncodePatternPacket> packets = collectEncodablePackets(false);
        if (packets.isEmpty()) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.ae2utility.recipe_tree_no_patterns"), true);
            }
            return;
        }
        for (EncodePatternPacket packet : packets) {
            PacketDistributor.sendToServer(packet);
        }
        this.onClose();
    }

    private List<EncodePatternPacket> collectEncodablePackets(boolean uploadMode) {
        List<EncodePatternPacket> packets = new ArrayList<>();
        for (RecipeTreeRecipeViewModel recipe : context.collectSelectedRecipes()) {
            if (hasExistingPatternForOutput(recipe)) {
                continue;
            }
            EncodePatternPacket packet = toEncodePacket(recipe);
            if (packet != null) {
                packets.add(uploadMode ? new EncodePatternPacket(packet.inputs(), packet.outputs(), packet.recipeId(), packet.patternName(),
                        packet.providerSearchKey(), packet.providerDisplayName(), true, packet.substitute(), packet.substituteFluids()) : packet);
            }
        }
        return packets;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pendingAlternativeSelection != null) {
            for (AlternativeOptionBounds option : alternativeOptionBounds) {
                if (option.contains(mouseX, mouseY)) {
                    selectAlternative(option.index());
                    return true;
                }
            }
            pendingAlternativeSelection = null;
        }
        if (pendingSelection != null) {
            for (CandidateCardBounds card : candidateCards) {
                if (card.contains(mouseX, mouseY)) {
                    selectCandidate(card.recipe());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (pendingAlternativeSelection != null && isInsideAlternativeViewport(mouseX, mouseY)
                && getAlternativeOptionCount() > getAlternativeVisibleCount()) {
            alternativeScroll -= (int) Math.signum(scrollY);
            clampAlternativeScroll();
            return true;
        }
        if (pendingSelection != null && isInsideSelectionViewport(mouseX, mouseY)
                && selectionContentHeight > getSelectionViewportHeight()) {
            selectionScroll -= (int) (scrollY * SCROLL_STEP);
            clampSelectionScroll();
            return true;
        }
        if (contentHeight > getViewportHeight()) {
            scrollOffset -= (int) (scrollY * SCROLL_STEP);
            clampScroll();
            rebuildRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, contentHeight - getViewportHeight());
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
    }

    private void clampSelectionScroll() {
        int maxScroll = Math.max(0, selectionContentHeight - getSelectionViewportHeight());
        selectionScroll = Math.max(0, Math.min(maxScroll, selectionScroll));
    }

    private int getViewportHeight() {
        return PANEL_HEIGHT - VIEW_TOP - VIEW_BOTTOM_PAD;
    }

    private int getTreeRight() {
        return pendingSelection == null ? leftPos + PANEL_WIDTH - 10 : getSelectionPanelX() - 8;
    }

    private int getSelectionPanelX() {
        return leftPos + PANEL_WIDTH - SELECTION_PANEL_WIDTH - 8;
    }

    private int getSelectionViewportTop() {
        return topPos + 40;
    }

    private int getSelectionViewportBottom() {
        return topPos + PANEL_HEIGHT - 28;
    }

    private int getSelectionViewportHeight() {
        return getSelectionViewportBottom() - getSelectionViewportTop();
    }

    private boolean isInsideSelectionViewport(double mouseX, double mouseY) {
        int x = getSelectionPanelX() + 4;
        return mouseX >= x && mouseX <= x + SELECTION_PANEL_WIDTH - 8
                && mouseY >= getSelectionViewportTop() && mouseY <= getSelectionViewportBottom();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // intentionally empty: super.render() will call this again internally,
        // so we draw the dim manually at the top of render() to avoid double-covering the white panel.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x66000000); // dim world view once, before everything
        renderPanel(graphics);
        renderRowBackgrounds(graphics);
        super.render(graphics, mouseX, mouseY, partialTick); // renderBackground() is a no-op now
        renderHeaderText(graphics);
        renderRowForegrounds(graphics);
        renderSelectionPanel(graphics, mouseX, mouseY);
        renderSelectionButtons(graphics, mouseX, mouseY, partialTick);
        renderAlternativeSelection(graphics, mouseX, mouseY);
        renderSelectionTooltip(graphics, mouseX, mouseY);
    }

    private void renderPanel(GuiGraphics graphics) {
        // 这里只画“主面板底图层”。
        // 适合放：外框、标题背景、正文区域底色、分隔线。
        // 不适合放：正文重复文字、会动态变化的行内容；否则容易和 renderRows 叠画后发糊。
        fillFramedPanel(graphics, leftPos, topPos, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT,
                0xFF1A1F24, 0xFF2B333B, 0xFFBEC7CE);

        int treeRight = getTreeRight();
        graphics.fill(leftPos + 8, topPos + 8, treeRight, topPos + HEADER_HEIGHT - 2, 0xFF46515A);
        // 主树正文区保持纯白底，避免和总览背景图混淆。
        graphics.fill(leftPos + 8, topPos + HEADER_HEIGHT, treeRight, topPos + PANEL_HEIGHT - 24, 0xFFFFFFFF);
        graphics.fill(leftPos + 8, topPos + HEADER_HEIGHT, treeRight, topPos + HEADER_HEIGHT + 1, 0xFF748089);
        // 这条线是正文区和下边栏之间的边界。
        // 如果你感觉“下边栏压住正文”或“正文离底栏太近”，通常从这条分隔线和底栏按钮的 y 位置一起调。
        graphics.fill(leftPos + 8, topPos + PANEL_HEIGHT - 25, treeRight, topPos + PANEL_HEIGHT - 24, 0xFF748089);

    }

    private void renderHeaderText(GuiGraphics graphics) {
        int treeRight = getTreeRight();
        int headerRight = overviewButton.visible ? overviewButton.getX() - 6 : treeRight - 6;
        graphics.drawString(this.font, trimToWidth(this.title, Math.max(40, headerRight - leftPos - 10)),
                leftPos + 10, topPos + 8, 0xFFEAF4FF, false);
        graphics.drawString(this.font, trimToWidth(context.title(), Math.max(80, headerRight - leftPos - 10)),
                leftPos + 10, topPos + 20, 0xFFFFFFFF, false);
    }

    private void renderRowBackgrounds(GuiGraphics graphics) {
        int viewTop = topPos + VIEW_TOP;
        int viewBottom = topPos + PANEL_HEIGHT - VIEW_BOTTOM_PAD;
        int viewLeft = leftPos + 8;
        int viewRight = getTreeRight();
        graphics.enableScissor(viewLeft, viewTop, viewRight, viewBottom);
        for (RowLayout row : rows) {
            int y = viewTop + row.relativeY() - scrollOffset;
            if (y + row.height() < viewTop || y > viewBottom) {
                continue;
            }
            int x = leftPos + 12 + row.indent() * INDENT;
            if (row.type() == RowType.NODE) {
                renderNodeRowBackground(graphics, x, y, viewRight);
            } else if (row.inputGroup() != null) {
                renderInputRowBackground(graphics, row.inputGroup(), x, y, viewRight);
            }
        }
        graphics.disableScissor();
    }

    private void renderRowForegrounds(GuiGraphics graphics) {
        int viewTop = topPos + VIEW_TOP;
        int viewBottom = topPos + PANEL_HEIGHT - VIEW_BOTTOM_PAD;
        int viewLeft = leftPos + 8;
        int viewRight = getTreeRight();
        graphics.enableScissor(viewLeft, viewTop, viewRight, viewBottom);
        for (RowLayout row : rows) {
            int y = viewTop + row.relativeY() - scrollOffset;
            if (y + row.height() < viewTop || y > viewBottom) {
                continue;
            }
            int x = leftPos + 12 + row.indent() * INDENT;
            if (row.type() == RowType.NODE) {
                renderNodeRowForeground(graphics, row.node().recipe(), x, y, viewRight);
            } else if (row.inputGroup() != null) {
                renderInputRowForeground(graphics, row.inputGroup(), x, y, viewRight);
            }
        }
        graphics.disableScissor();
    }

    private void renderNodeRowBackground(GuiGraphics graphics, int x, int y, int treeRight) {
        graphics.fill(x - 3, y, treeRight - 4, y + NODE_HEIGHT - 3, 0xFFC9D4DF);
        graphics.fill(x - 2, y + 1, treeRight - 5, y + NODE_HEIGHT - 4, 0xFFE1E8EE);
        graphics.fill(x, y + 3, treeRight - 7, y + NODE_HEIGHT - 6, 0xFFFFFFFF);
    }

    private void renderNodeRowForeground(GuiGraphics graphics, RecipeTreeRecipeViewModel recipe, int x, int y, int treeRight) {
        renderIngredientAt(graphics, recipe.primaryOutputIngredient(), x, y + 6);
        graphics.drawString(this.font, trimToWidth(recipe.title(), Math.max(40, treeRight - x - 28)),
                x + 20, y + 6, 0xFF1D2429, false);
        Component subtitle = recipe.subtitle();
        if (subtitle != null && !subtitle.getString().isBlank()) {
            graphics.drawString(this.font, trimToWidth(subtitle.copy().withStyle(ChatFormatting.DARK_GRAY),
                    Math.max(40, treeRight - x - 28)), x + 20, y + 17, 0xFF5E6971, false);
        }
    }

    private void renderInputRowBackground(GuiGraphics graphics, InputGroup inputGroup, int x, int y, int treeRight) {
        if (inputGroup.child() != null) {
            graphics.fill(x - 2, y + 1, treeRight - 4, y + INPUT_HEIGHT - 1, 0xFFAED8A0);
        }
    }

    private void renderInputRowForeground(GuiGraphics graphics, InputGroup inputGroup, int x, int y, int treeRight) {
        renderIngredientAt(graphics, inputGroup.displayIngredient(), x, y + 1);
        String name = inputGroup.displayName();
        String amountText = inputGroup.amountText();
        Component label = name.isBlank()
                ? Component.translatable("gui.ae2utility.recipe_tree.unknown_input")
                : Component.literal(amountText.isBlank() ? name : name + " " + amountText);
        graphics.drawString(this.font, trimToWidth(label, Math.max(34, treeRight - x - 40)),
                x + 20, y + 6, 0xFF263238, false);
    }

    private void renderSelectionPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        candidateCards.clear();
        candidateSlots.clear();
        if (pendingSelection == null) {
            return;
        }

        int panelX = getSelectionPanelX();
        int panelY = topPos + 8;
        fillFramedPanel(graphics, panelX, panelY, panelX + SELECTION_PANEL_WIDTH, topPos + PANEL_HEIGHT - 8,
                0xFF30373D, 0xFF4D5962, 0xFFE8EEF2);
        graphics.fill(panelX + 4, panelY + 18, panelX + SELECTION_PANEL_WIDTH - 4, panelY + 48, 0xFF46515A);

        String inputName = pendingSelection.inputGroup().displayName();
        if (inputName.isBlank()) {
            inputName = Component.translatable("gui.ae2utility.recipe_tree.unknown_input").getString();
        }
        graphics.drawString(this.font,
                trimToWidth(Component.translatable("gui.ae2utility.recipe_tree.choose_recipe_for", inputName),
                        SELECTION_PANEL_WIDTH - 14),
                panelX + 6, panelY + 6, 0xFF1F252A, false);
        SelectionGroup currentGroup = getCurrentSelectionGroup();
        if (currentGroup != null) {
            graphics.drawCenteredString(this.font, trimToWidth(currentGroup.label(), SELECTION_PANEL_WIDTH - 56),
                    panelX + SELECTION_PANEL_WIDTH / 2, panelY + 22, 0xFFFFFFFF);
            graphics.drawCenteredString(this.font,
                    Component.literal((selectionGroupIndex + 1) + "/" + pendingSelection.groups().size()),
                    panelX + SELECTION_PANEL_WIDTH / 2, panelY + 34, 0xFFEAF4FF);
        }

        int viewportLeft = panelX + 4;
        int viewportRight = panelX + SELECTION_PANEL_WIDTH - 4;
        int viewportTop = getSelectionViewportTop() + 22;
        int viewportBottom = getSelectionViewportBottom() - 4;
        graphics.enableScissor(viewportLeft, viewportTop, viewportRight, viewportBottom);

        if (currentGroup == null || currentGroup.recipes().isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.ae2utility.recipe_tree.no_candidates"),
                    viewportLeft + 6, viewportTop + 6, 0xFF545F66, false);
            selectionContentHeight = 0;
            graphics.disableScissor();
            return;
        }

        int y = viewportTop - selectionScroll;
        int cardWidth = SELECTION_PANEL_WIDTH - 12;
        for (RecipeTreeRecipeViewModel candidate : currentGroup.recipes()) {
            int cardX = panelX + 6;
            if (y + SELECTION_CARD_HEIGHT >= viewportTop && y <= viewportBottom) {
                boolean hovered = mouseX >= cardX && mouseX <= cardX + cardWidth
                        && mouseY >= y && mouseY <= y + SELECTION_CARD_HEIGHT;
                renderCandidateCard(graphics, candidate, cardX, y, cardWidth, hovered);
                candidateCards.add(new CandidateCardBounds(candidate, cardX, y, cardWidth, SELECTION_CARD_HEIGHT));
            }
            y += SELECTION_CARD_HEIGHT + SELECTION_CARD_GAP;
        }
        selectionContentHeight = currentGroup.recipes().size() * (SELECTION_CARD_HEIGHT + SELECTION_CARD_GAP)
                - SELECTION_CARD_GAP;
        clampSelectionScroll();
        graphics.disableScissor();
    }

    private void renderSelectionButtons(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (pendingSelection == null) {
            return;
        }
        if (cancelSelectionButton.visible) {
            cancelSelectionButton.render(graphics, mouseX, mouseY, partialTick);
        }
        if (prevGroupButton.visible) {
            prevGroupButton.render(graphics, mouseX, mouseY, partialTick);
        }
        if (nextGroupButton.visible) {
            nextGroupButton.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderAlternativeSelection(GuiGraphics graphics, int mouseX, int mouseY) {
        alternativeOptionBounds.clear();
        if (pendingAlternativeSelection == null) {
            return;
        }

        InputGroup inputGroup = pendingAlternativeSelection.inputGroup();
        List<DisplayOption> alternatives = inputGroup.alternatives();
        if (alternatives.isEmpty()) {
            pendingAlternativeSelection = null;
            return;
        }

        int visibleCount = getAlternativeVisibleCount();
        int panelWidth = 124;
        int panelHeight = visibleCount * 18 + 6;
        int panelX = Math.min(getTreeRight() - panelWidth - 6, leftPos + PANEL_WIDTH - panelWidth - 10);
        int panelY = Math.min(topPos + PANEL_HEIGHT - panelHeight - 28, topPos + VIEW_TOP + 8);
        fillFramedPanel(graphics, panelX, panelY, panelX + panelWidth, panelY + panelHeight,
                0xFF30373D, 0xFF4D5962, 0xFFE8EEF2);

        clampAlternativeScroll();
        int start = alternativeScroll;
        int end = Math.min(alternatives.size(), start + visibleCount);
        for (int i = start; i < end; i++) {
            int optionY = panelY + 4 + (i - start) * 18;
            DisplayOption option = alternatives.get(i);
            boolean selected = i == inputGroup.selectedAlternativeIndex();
            if (selected) {
                graphics.fill(panelX + 3, optionY, panelX + panelWidth - 3, optionY + 17, 0x3358A6FF);
            }
            renderTypedSlot(graphics, panelX + 4, optionY, option.typedIngredient());
            graphics.drawString(this.font, trimToWidth(Component.literal(option.label()), panelWidth - 28),
                    panelX + 24, optionY + 5, 0xFF263238, false);
            alternativeOptionBounds.add(new AlternativeOptionBounds(i, option.typedIngredient(), panelX + 3, optionY, panelWidth - 6, 17));
        }
    }

    private void renderCandidateCard(GuiGraphics graphics, RecipeTreeRecipeViewModel recipe, int x, int y, int width,
            boolean hovered) {
        int outer = hovered ? 0xFF6FA6D8 : 0xFF49545D;
        int inner = hovered ? 0xFFF8FBFD : 0xFFE3EAEE;
        graphics.fill(x, y, x + width, y + SELECTION_CARD_HEIGHT, outer);
        graphics.fill(x + 1, y + 1, x + width - 1, y + SELECTION_CARD_HEIGHT - 1, inner);
        graphics.fill(x + 3, y + 30, x + width - 3, y + 31, 0xFFAFB8BE);

        graphics.drawString(this.font, trimToWidth(recipe.title(), width - 34), x + 6, y + 5, 0xFF1E252A, false);
        if (recipe.subtitleIcon() != null) {
            recipe.subtitleIcon().draw(graphics, x + 6, y + 14);
        }
        Component subtitle = recipe.subtitle();
        if (subtitle != null && !subtitle.getString().isBlank()) {
            graphics.drawString(this.font, trimToWidth(subtitle, width - 30), x + 24, y + 18, 0xFF58646C, false);
        }

        int inputX = x + 6;
        int inputY = y + 36;
        List<RecipeTreeInputViewModel> inputs = recipe.inputs();
        int maxSlots = Math.min(inputs.size(), 6);
        for (int i = 0; i < maxSlots; i++) {
            int slotX = inputX + (i % 3) * 18;
            int slotY = inputY + (i / 3) * 18;
            ITypedIngredient<?> ingredient = inputs.get(i).displayIngredient();
            renderTypedSlot(graphics, slotX, slotY, ingredient);
            candidateSlots.add(new CandidateSlotBounds(ingredient, slotX, slotY, 18, 18));
        }
        if (inputs.size() > 6) {
            graphics.drawString(this.font, Component.literal("+" + (inputs.size() - 6)),
                    inputX + 38, inputY + 36, 0xFF566168, false);
        }

        graphics.drawString(this.font, Component.literal(">"), x + 66, y + 46, 0xFF4A565F, false);
        int outputX = x + width - 24;
        int outputY = y + 40;
        ITypedIngredient<?> output = recipe.primaryOutputIngredient();
        renderTypedSlot(graphics, outputX, outputY, output);
        candidateSlots.add(new CandidateSlotBounds(output, outputX, outputY, 18, 18));
    }

    private void renderSlot(GuiGraphics graphics, int x, int y, ItemStack stack) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF8C969D);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFFC9D2D8);
        graphics.fill(x + 2, y + 2, x + 16, y + 16, 0xFF5C6770);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 1, y + 1);
            graphics.renderItemDecorations(this.font, stack, x + 1, y + 1);
        }
    }

    private void renderTypedSlot(GuiGraphics graphics, int x, int y, @Nullable ITypedIngredient<?> ingredient) {
        ItemStack itemStack = extractItemStack(ingredient);
        renderSlot(graphics, x, y, itemStack);
        if (itemStack.isEmpty() && ingredient != null) {
            renderIngredientAt(graphics, ingredient, x + 1, y + 1);
        }
    }

    private void renderIngredientAt(GuiGraphics graphics, @Nullable ITypedIngredient<?> ingredient, int x, int y) {
        if (ingredient == null) {
            return;
        }
        ItemStack itemStack = extractItemStack(ingredient);
        if (!itemStack.isEmpty()) {
            graphics.renderItem(itemStack, x, y);
            return;
        }
        renderJeiIngredient(graphics, ingredient, x, y);
    }

    private void renderJeiIngredient(GuiGraphics graphics, ITypedIngredient<?> ingredient, int x, int y) {
        IIngredientManager ingredientManager = getIngredientManager();
        if (ingredientManager == null) {
            return;
        }
        renderJeiIngredientTyped(graphics, ingredientManager, ingredient, x, y);
    }

    private <T> void renderJeiIngredientTyped(GuiGraphics graphics, IIngredientManager ingredientManager, ITypedIngredient<?> ingredient,
            int x, int y) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> typed = (ITypedIngredient<T>) ingredient;
        IIngredientRenderer<T> renderer = ingredientManager.getIngredientRenderer(typed.getType());
        renderer.render(graphics, typed.getIngredient(), x, y);
    }

    private static ItemStack extractItemStack(@Nullable ITypedIngredient<?> ingredient) {
        return ingredient == null
                ? ItemStack.EMPTY
                : ingredient.getIngredient(mezz.jei.api.constants.VanillaTypes.ITEM_STACK).map(ItemStack::copy).orElse(ItemStack.EMPTY);
    }

    private void fillFramedPanel(GuiGraphics graphics, int left, int top, int right, int bottom, int border, int middle,
            int inner) {
        graphics.fill(left, top, right, bottom, border);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, middle);
        graphics.fill(left + 3, top + 3, right - 3, bottom - 3, inner);
    }

    private Component trimToWidth(Component text, int maxWidth) {
        return Component.literal(this.font.substrByWidth(text, Math.max(8, maxWidth)).getString());
    }

    private @Nullable EncodePatternPacket toEncodePacket(RecipeTreeRecipeViewModel recipe) {
        GenericStack output = toGenericStack(recipe.primaryOutputIngredient(), recipe.primaryOutputCount());
        if (output == null) {
            return null;
        }

        List<List<GenericStack>> inputs = new ArrayList<>();
        for (RecipeTreeInputViewModel input : recipe.inputs()) {
            List<GenericStack> alternatives = new ArrayList<>();
            RequestedIngredient requested = input.requestedIngredient();
            if (requested != null) {
                for (ItemStack alternative : requested.alternatives()) {
                    if (!alternative.isEmpty()) {
                        alternatives.add(GenericStack.fromItemStack(alternative.copyWithCount(Math.max(1, requested.count()))));
                    }
                }
            } else {
                for (DisplayOption option : input.displayOptions()) {
                    GenericStack generic = toGenericStack(option.typedIngredient(), input.amount());
                    if (generic != null) {
                        alternatives.add(generic);
                    }
                }
            }
            inputs.add(alternatives.isEmpty() ? null : alternatives);
        }

        List<GenericStack> outputs = List.of(output);
        return new EncodePatternPacket(inputs, outputs, recipe.recipeId(), recipe.title().getString(),
                deriveProviderSearchKey(recipe), deriveProviderDisplayName(recipe), false, JeiPatternSubstitutionUi.isItemSubstituteOn(),
                JeiPatternSubstitutionUi.isFluidSubstituteOn());
    }

    private static String deriveProviderSearchKey(RecipeTreeRecipeViewModel recipe) {
        if (!net.neoforged.fml.ModList.get().isLoaded("extendedae_plus")) {
            return "";
        }
        try {
            Class<?> uploadUtil = Class.forName("com.extendedae_plus.util.uploadPattern.ExtendedAEPatternUploadUtil");
            if (recipe.recipeId() != null) {
                var minecraft = Minecraft.getInstance();
                if (minecraft.level != null) {
                    var recipeHolder = minecraft.level.getRecipeManager().byKey(recipe.recipeId()).orElse(null);
                    if (recipeHolder != null) {
                        if (recipeHolder.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe) {
                            java.lang.reflect.Field defaultKey = uploadUtil.getField("DEFAULT_CRAFTING_SEARCH_KEY");
                            return (String) defaultKey.get(null);
                        }
                        java.lang.reflect.Method mapRecipe = uploadUtil.getMethod("mapRecipeTypeToSearchKey",
                                net.minecraft.world.item.crafting.Recipe.class);
                        Object key = mapRecipe.invoke(null, recipeHolder.value());
                        if (key instanceof String s && !s.isBlank()) {
                            return s;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        Component subtitle = recipe.subtitle();
        return subtitle == null ? "" : subtitle.getString();
    }

    private static String deriveProviderDisplayName(RecipeTreeRecipeViewModel recipe) {
        Component subtitle = recipe.subtitle();
        if (subtitle != null && !subtitle.getString().isBlank()) {
            return subtitle.getString();
        }
        String searchKey = deriveProviderSearchKey(recipe);
        return searchKey == null ? "" : searchKey;
    }

    private static @Nullable GenericStack toGenericStack(@Nullable ITypedIngredient<?> ingredient, int amount) {
        return GenericIngredientUtil.toGenericStack(ingredient, amount);
    }

    private void renderSelectionTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (RowLayout row : rows) {
            if (row.plusButton() != null && !row.plusButton().active && isPointInsideButton(row.plusButton(), mouseX, mouseY)) {
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable("gui.ae2utility.recipe_tree.pattern_exists").withStyle(ChatFormatting.RED)),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
        }
        if (applyButton.visible && !applyButton.active && isPointInsideButton(applyButton, mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    List.of(Component.translatable("gui.ae2utility.recipe_tree.upload_missing_eaep").withStyle(ChatFormatting.RED)),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        for (AlternativeOptionBounds option : alternativeOptionBounds) {
            if (option.contains(mouseX, mouseY) && option.ingredient() != null) {
                renderIngredientTooltip(graphics, option.ingredient(), mouseX, mouseY);
                return;
            }
        }
        if (pendingSelection == null) {
            return;
        }
        for (CandidateSlotBounds slot : candidateSlots) {
            if (slot.contains(mouseX, mouseY) && slot.ingredient() != null) {
                renderIngredientTooltip(graphics, slot.ingredient(), mouseX, mouseY);
                return;
            }
        }
    }

    private void renderIngredientTooltip(GuiGraphics graphics, ITypedIngredient<?> ingredient, int mouseX, int mouseY) {
        ItemStack itemStack = extractItemStack(ingredient);
        if (!itemStack.isEmpty()) {
            graphics.renderTooltip(this.font, itemStack, mouseX, mouseY);
            return;
        }
        IIngredientManager ingredientManager = getIngredientManager();
        if (ingredientManager == null) {
            return;
        }
        renderIngredientTooltipTyped(graphics, ingredientManager, ingredient, mouseX, mouseY);
    }

    private <T> void renderIngredientTooltipTyped(GuiGraphics graphics, IIngredientManager ingredientManager, ITypedIngredient<?> ingredient,
            int mouseX, int mouseY) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> typed = (ITypedIngredient<T>) ingredient;
        IIngredientRenderer<T> renderer = ingredientManager.getIngredientRenderer(typed.getType());
        List<Component> tooltip = renderer.getTooltip(typed.getIngredient(), TooltipFlag.Default.NORMAL);
        if (!tooltip.isEmpty()) {
            graphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
        }
    }

    private static @Nullable IIngredientManager getIngredientManager() {
        var runtime = Ae2UtilityJeiPlugin.getJeiRuntime();
        return runtime == null ? null : runtime.getIngredientManager();
    }

    private @Nullable SelectionGroup getCurrentSelectionGroup() {
        if (pendingSelection == null || pendingSelection.groups().isEmpty()) {
            return null;
        }
        int index = Math.max(0, Math.min(pendingSelection.groups().size() - 1, selectionGroupIndex));
        return pendingSelection.groups().get(index);
    }

    private void selectAlternative(int index) {
        if (pendingAlternativeSelection == null) {
            return;
        }
        for (RecipeTreeInputViewModel member : pendingAlternativeSelection.inputGroup().members()) {
            member.selectAlternative(index);
        }
        pendingAlternativeSelection = null;
        rebuildRows();
    }

    private List<InputGroup> groupInputs(RecipeTreeNodeViewModel node) {
        Map<String, List<RecipeTreeInputViewModel>> grouped = new LinkedHashMap<>();
        for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
            grouped.computeIfAbsent(signatureOf(input), key -> new ArrayList<>()).add(input);
        }

        List<InputGroup> groups = new ArrayList<>();
        for (List<RecipeTreeInputViewModel> members : grouped.values()) {
            groups.add(new InputGroup(members));
        }
        return groups;
    }

    private List<SelectionGroup> groupCandidates(List<RecipeTreeRecipeViewModel> candidates) {
        Map<String, List<RecipeTreeRecipeViewModel>> grouped = new LinkedHashMap<>();
        for (RecipeTreeRecipeViewModel candidate : candidates) {
            String key = candidate.subtitle() == null || candidate.subtitle().getString().isBlank()
                    ? candidate.title().getString()
                    : candidate.subtitle().getString();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(candidate);
        }

        List<SelectionGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<RecipeTreeRecipeViewModel>> entry : grouped.entrySet()) {
            groups.add(new SelectionGroup(Component.literal(entry.getKey()), List.copyOf(entry.getValue())));
        }
        groups.sort((left, right) -> Integer.compare(selectionGroupPriority(left), selectionGroupPriority(right)));
        return groups;
    }

    private static int selectionGroupPriority(SelectionGroup group) {
        String label = group.label().getString().toLowerCase(java.util.Locale.ROOT);
        if (label.contains("crafting") || label.contains("合成")) {
            return 0;
        }
        return 1;
    }

    private String signatureOf(RecipeTreeInputViewModel input) {
        var requested = input.requestedIngredient();
        if (requested != null && !requested.alternatives().isEmpty()) {
            return signatureOf(requested);
        }
        ItemStack stack = input.displayStack();
        if (!stack.isEmpty()) {
            return signatureOfItemType(stack);
        }
        ITypedIngredient<?> ingredient = input.displayIngredient();
        if (ingredient != null) {
            return signatureOfMaterialIngredient(ingredient);
        }
        return "name#" + input.displayName();
    }

    private String signatureOf(RequestedIngredient ingredient) {
        List<String> parts = new ArrayList<>();
        for (ItemStack alternative : ingredient.alternatives()) {
            if (!alternative.isEmpty()) {
                parts.add(signatureOfItemType(alternative));
            }
        }
        parts.sort(String::compareTo);
        return "requested#" + String.join("|", parts);
    }

    private String signatureOfMaterialIngredient(ITypedIngredient<?> ingredient) {
        ItemStack itemStack = ingredient.getIngredient(VanillaTypes.ITEM_STACK).map(ItemStack::copy).orElse(ItemStack.EMPTY);
        if (!itemStack.isEmpty()) {
            return signatureOfItemType(itemStack);
        }
        Object raw = ingredient.getIngredient();
        var aeKey = GenericIngredientUtil.toAEKey(raw);
        if (aeKey != null) {
            return "ae#" + aeKey;
        }
        IIngredientManager ingredientManager = getIngredientManager();
        if (ingredientManager != null) {
            return signatureOfTypedIngredient(ingredientManager, ingredient);
        }
        return "typed#" + raw.getClass().getName() + "#" + raw;
    }

    private String signatureOfItemType(ItemStack stack) {
        return "itemtype#" + stack.getItem();
    }

    private boolean hasExistingPatternForInput(InputGroup inputGroup) {
        return inputGroup != null && hasCraftableKey(inputGroup.displayIngredient());
    }

    private boolean hasExistingPatternForOutput(RecipeTreeRecipeViewModel recipe) {
        return recipe != null && hasCraftableKey(recipe.primaryOutputIngredient());
    }

    private boolean hasCraftableKey(@Nullable ITypedIngredient<?> ingredient) {
        return ingredient != null && CraftableStateCache.isCraftable(GenericIngredientUtil.toAEKey(ingredient.getIngredient()));
    }

    private static boolean isPointInsideButton(Button button, double mouseX, double mouseY) {
        return button != null && button.visible
                && mouseX >= button.getX() && mouseX <= button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY <= button.getY() + button.getHeight();
    }

    private void applyChildRecipeReuse(RecipeTreeNodeViewModel parent, InputGroup selectedGroup, RecipeTreeRecipeViewModel selectedRecipe) {
        String selectedSignature = signatureOf(selectedGroup.representative());
        context.rememberSelection(selectedSignature, selectedRecipe);
        for (RecipeTreeInputViewModel input : parent.recipe().inputs()) {
            if (input.child() != null) {
                continue;
            }
            if (!signatureOf(input).equals(selectedSignature)) {
                continue;
            }
            input.setChild(new RecipeTreeNodeViewModel(selectedRecipe, parent));
        }
        rebuildRows();
    }

    private void autoApplyRememberedChild(RecipeTreeNodeViewModel parent, InputGroup inputGroup) {
        if (inputGroup.child() != null) {
            return;
        }
        String signature = signatureOf(inputGroup.representative());
        RecipeTreeRecipeViewModel remembered = context.getRememberedSelection(signature);
        if (remembered == null || parent.containsRecipe(remembered)) {
            return;
        }
        RecipeTreeNodeViewModel childNode = new RecipeTreeNodeViewModel(remembered, parent);
        for (RecipeTreeInputViewModel input : inputGroup.members()) {
            if (input.child() == null) {
                input.setChild(childNode);
            }
        }
    }

    private int getAlternativeVisibleCount() {
        return 8;
    }

    private int getAlternativeOptionCount() {
        return pendingAlternativeSelection == null ? 0 : pendingAlternativeSelection.inputGroup().alternatives().size();
    }

    private void clampAlternativeScroll() {
        int maxScroll = Math.max(0, getAlternativeOptionCount() - getAlternativeVisibleCount());
        alternativeScroll = Math.max(0, Math.min(maxScroll, alternativeScroll));
    }

    private boolean isInsideAlternativeViewport(double mouseX, double mouseY) {
        if (pendingAlternativeSelection == null) {
            return false;
        }
        int panelWidth = 124;
        int panelHeight = getAlternativeVisibleCount() * 18 + 6;
        int panelX = Math.min(getTreeRight() - panelWidth - 6, leftPos + PANEL_WIDTH - panelWidth - 10);
        int panelY = Math.min(topPos + PANEL_HEIGHT - panelHeight - 28, topPos + VIEW_TOP + 8);
        return mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= panelY && mouseY <= panelY + panelHeight;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(returnScreen);
    }

    private enum RowType {
        NODE,
        INPUT
    }

    private record RowLayout(RowType type, RecipeTreeNodeViewModel node, InputGroup inputGroup, Button altButton, Button clearButton, Button plusButton,
            int indent, int relativeY, int height) {
    }

    private record PendingSelection(RecipeTreeNodeViewModel parent, InputGroup inputGroup,
            List<RecipeTreeRecipeViewModel> candidates, List<SelectionGroup> groups) {
    }

    private record CandidateCardBounds(RecipeTreeRecipeViewModel recipe, int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private record CandidateSlotBounds(@Nullable ITypedIngredient<?> ingredient, int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private record AlternativeOptionBounds(int index, @Nullable ITypedIngredient<?> ingredient, int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private record SelectionGroup(Component label, List<RecipeTreeRecipeViewModel> recipes) {
    }

    private static final class InputGroup {
        private final List<RecipeTreeInputViewModel> members;
        private final RecipeTreeInputViewModel representative;
        private final int totalCount;
        private final @Nullable RecipeTreeNodeViewModel child;

        private InputGroup(List<RecipeTreeInputViewModel> members) {
            this.members = List.copyOf(members);
            this.representative = members.get(0);
            int count = 0;
            RecipeTreeNodeViewModel resolvedChild = null;
            for (RecipeTreeInputViewModel member : members) {
                count += member.amount();
                if (resolvedChild == null) {
                    resolvedChild = member.child();
                }
            }
            this.totalCount = Math.max(1, count);
            this.child = resolvedChild;
        }

        private List<RecipeTreeInputViewModel> members() {
            return members;
        }

        private RecipeTreeInputViewModel representative() {
            return representative;
        }

        private ItemStack displayStack() {
            return representative.displayStack();
        }

        private @Nullable ITypedIngredient<?> displayIngredient() {
            return representative.displayIngredient();
        }

        private String displayName() {
            return representative.displayName();
        }

        private String amountText() {
            RequestedIngredient requested = representative.requestedIngredient();
            if (requested != null) {
                return "x" + formatCompactCount(totalCount);
            }
            return representative.amountText();
        }

        private int totalCount() {
            return totalCount;
        }

        private @Nullable RecipeTreeNodeViewModel child() {
            return child;
        }

        private boolean hasAlternativeChoices() {
            return representative.hasAlternativeChoices();
        }

        private List<DisplayOption> alternatives() {
            return representative.displayOptions();
        }

        private int selectedAlternativeIndex() {
            return representative.selectedAlternativeIndex();
        }
    }

    private record PendingAlternativeSelection(InputGroup inputGroup) {
    }

    private static String signatureOfTypedIngredient(IIngredientManager ingredientManager, ITypedIngredient<?> ingredient) {
        return signatureOfTypedIngredientTyped(ingredientManager, ingredient);
    }

    private static <T> String signatureOfTypedIngredientTyped(IIngredientManager ingredientManager, ITypedIngredient<?> ingredient) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> typed = (ITypedIngredient<T>) ingredient;
        return typed.getType().getUid() + "#" + ingredientManager.getIngredientHelper(typed.getType()).getUid(typed,
                mezz.jei.api.ingredients.subtypes.UidContext.Ingredient);
    }

    private static String formatCompactCount(int count) {
        if (count < 1000) {
            return Integer.toString(count);
        }
        double value = count;
        String[] suffixes = { "K", "M", "B" };
        int suffixIndex = -1;
        while (value >= 1000.0D && suffixIndex + 1 < suffixes.length) {
            value /= 1000.0D;
            suffixIndex++;
        }
        if (suffixIndex < 0) {
            return Integer.toString(count);
        }
        if (value >= 100.0D || Math.abs(value - Math.round(value)) < 0.05D) {
            return ((int) Math.round(value)) + suffixes[suffixIndex];
        }
        return String.format(java.util.Locale.ROOT, "%.1f%s", value, suffixes[suffixIndex]);
    }

    private static final class PlaceholderReturnScreen extends Screen {
        private PlaceholderReturnScreen() {
            super(Component.empty());
        }
    }
}
