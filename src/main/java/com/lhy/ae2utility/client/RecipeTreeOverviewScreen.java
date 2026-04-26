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
import com.lhy.ae2utility.jei.CraftableStateCache;
import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;
import com.lhy.ae2utility.util.GenericIngredientUtil;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class RecipeTreeOverviewScreen extends Screen {
    private static final int NODE_HEIGHT = 26;
    private static final int LEVEL_GAP = 44;
    private static final int LEAF_GAP = 16;
    private static final int BG_TILE_WIDTH = 256;
    private static final int BG_TILE_HEIGHT = 256;
    private static final int MACHINE_SLOT_SIZE = 18;
    private static final int SELECTION_PANEL_WIDTH = 154;
    private static final int SELECTION_CARD_HEIGHT = 78;
    private static final int SELECTION_CARD_GAP = 6;
    private static final int SCROLL_STEP = 12;
    private static final int TOP_MATERIALS_OFFSET = 48;
    private static final ResourceLocation BG_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("ae2utility", "textures/gui/recipe_tree_bg.png");

    private final RecipeTreeRootContext context;
    private final Screen returnScreen;
    private final List<PositionedNode> positionedNodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private final List<CandidateCardBounds> candidateCards = new ArrayList<>();
    private final List<CandidateSlotBounds> candidateSlots = new ArrayList<>();
    private final List<TopMaterialBounds> topMaterialBounds = new ArrayList<>();
    private List<RequestedIngredient> topMaterials = List.of();

    private GraphNode rootNode;
    private Button backButton;
    private Button toggleExistingPatternButton;
    private Button cancelSelectionButton;
    private Button prevGroupButton;
    private Button nextGroupButton;
    private double panX;
    private double panY;
    private double zoom = 1.0;
    private boolean initializedPan;
    private int selectionScroll;
    private int selectionContentHeight;
    private int selectionGroupIndex;
    private @Nullable PendingSelection pendingSelection;

    public RecipeTreeOverviewScreen(RecipeTreeRootContext context, Screen returnScreen) {
        super(Component.translatable("gui.ae2utility.recipe_tree.overview_title"));
        this.context = context;
        this.returnScreen = returnScreen;
    }

    @Override
    protected void init() {
        super.init();
        this.backButton = Button.builder(Component.translatable("gui.ae2utility.recipe_tree.back"),
                btn -> onClose()).bounds(this.width - 68, 10, 60, 20).build();
        this.toggleExistingPatternButton = Button.builder(Component.empty(),
                btn -> {
                    context.setDisableExistingPatternExpansion(!context.disableExistingPatternExpansion());
                    syncToggleExistingPatternButton();
                }).bounds(this.width - 166, 10, 90, 20).build();
        this.cancelSelectionButton = Button.builder(Component.literal("取消"),
                btn -> {
                    clearPendingSelection();
                    closeSelection();
                    rebuildLayout();
                }).bounds(0, 0, 54, 18).build();
        this.prevGroupButton = Button.builder(Component.literal("<"), btn -> switchSelectionGroup(-1))
                .bounds(0, 0, 18, 18).build();
        this.nextGroupButton = Button.builder(Component.literal(">"), btn -> switchSelectionGroup(1))
                .bounds(0, 0, 18, 18).build();

        this.addRenderableWidget(backButton);
        this.addRenderableWidget(toggleExistingPatternButton);
        this.addRenderableWidget(cancelSelectionButton);
        this.addRenderableWidget(prevGroupButton);
        this.addRenderableWidget(nextGroupButton);
        syncToggleExistingPatternButton();
        updateSelectionButtons();
        rebuildLayout();
    }

    private void rebuildLayout() {
        this.rootNode = buildGraph(context.root(), 1);
        this.topMaterials = context.collectRequestedIngredients();
        positionedNodes.clear();
        edges.clear();
        PositionedNode root = layoutNode(rootNode, 0, 36);
        if (!initializedPan) {
            panX = this.width * 0.5D - (root.x() + rootNode.width() / 2.0D);
            panY = 48D - root.y() + TOP_MATERIALS_OFFSET;
            initializedPan = true;
        }
    }

    private PositionedNode layoutNode(GraphNode node, int depth, int left) {
        int y = 42 + TOP_MATERIALS_OFFSET + depth * LEVEL_GAP;
        int subtreeWidth = computeSubtreeWidth(node);
        int x = left + Math.max(0, (subtreeWidth - node.width()) / 2);
        PositionedNode positioned = new PositionedNode(node, x, y);
        positionedNodes.add(positioned);

        if (node.children().isEmpty()) {
            return positioned;
        }

        int childrenWidth = totalChildrenWidth(node.children());
        int childLeft = left + Math.max(0, (subtreeWidth - childrenWidth) / 2);
        for (GraphNode child : node.children()) {
            PositionedNode childPositioned = layoutNode(child, depth + 1, childLeft);
            edges.add(new Edge(positioned, childPositioned));
            childLeft += computeSubtreeWidth(child) + LEAF_GAP;
        }
        return positioned;
    }

    private int computeSubtreeWidth(GraphNode node) {
        if (node.children().isEmpty()) {
            return node.width();
        }
        return Math.max(node.width(), totalChildrenWidth(node.children()));
    }

    private int totalChildrenWidth(List<GraphNode> children) {
        int total = 0;
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                total += LEAF_GAP;
            }
            total += computeSubtreeWidth(children.get(i));
        }
        return total;
    }

    private GraphNode buildGraph(RecipeTreeNodeViewModel node, int crafts) {
        List<GraphNode> children = new ArrayList<>();
        Map<String, List<RecipeTreeInputViewModel>> groupedInputs = new LinkedHashMap<>();
        for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
            groupedInputs.computeIfAbsent(leafSignatureOf(input), k -> new ArrayList<>()).add(input);
        }

        Map<String, MergedLeaf> mergedLeaves = new LinkedHashMap<>();
        for (Map.Entry<String, List<RecipeTreeInputViewModel>> entry : groupedInputs.entrySet()) {
            String key = entry.getKey();
            List<RecipeTreeInputViewModel> group = entry.getValue();
            RecipeTreeInputViewModel representative = group.get(0);

            int totalRequiredAmount = 0;
            for (RecipeTreeInputViewModel input : group) {
                int amount = safeMultiply(crafts, input.amount());
                totalRequiredAmount = safeAdd(totalRequiredAmount, Math.max(1, amount));
            }

            RecipeTreeNodeViewModel child = representative.child();

            // Attempt to apply remembered recipe if no child is explicitly set
            if (child == null) {
                String signature = signatureOf(representative);
                RecipeTreeRecipeViewModel remembered = context.getRememberedSelection(signature);
                if (remembered != null && !containsRecipeInAncestors(node, remembered)) {
                    child = new RecipeTreeNodeViewModel(remembered, node);
                    for (RecipeTreeInputViewModel input : group) {
                        input.setChild(child);
                    }
                }
            }
            
            if (child != null) {
                int childCrafts = ceilDiv(totalRequiredAmount, child.recipe().primaryOutputCount());
                children.add(buildGraph(child, childCrafts));
            } else {
                // This branch is reached if no explicit child, and no remembered recipe was applied (or it was circular)
                mergedLeaves.put(key, new MergedLeaf(representative.displayIngredient(), displayNameOf(representative), totalRequiredAmount, representative.amountText(),
                        node, group));
            }
        }

        for (MergedLeaf leaf : mergedLeaves.values()) {
            String amountLabel = formatMergedLeafAmount(leaf);
            children.add(new GraphNode(leaf.ingredient(), leaf.title(), amountLabel, exactAmountOf(leaf), null, null, null, leaf,
                    List.of(), computeNodeWidth(amountLabel, false)));
        }
        String amountLabel = crafts > 1 ? "x" + formatCompactCount(crafts) : "";
        return new GraphNode(node.recipe().primaryOutputIngredient(), node.recipe().title().getString(), amountLabel,
                crafts > 1 ? "数量: " + crafts : "", node, node.recipe().subtitleIcon(), node.recipe().subtitle(), null, children,
                computeNodeWidth(amountLabel, node.recipe().subtitleIcon() != null));
    }

    private static int computeNodeWidth(String amountLabel, boolean hasMachineIcon) {
        int width = 24 + (amountLabel == null || amountLabel.isBlank() ? 0 : Minecraft.getInstance().font.width(amountLabel) + 6);
        if (hasMachineIcon) {
            width += MACHINE_SLOT_SIZE + 4;
        }
        return Math.max(24, width);
    }

    private static String displayNameOf(RecipeTreeInputViewModel input) {
        String name = input.displayName();
        return name == null || name.isBlank()
                ? Component.translatable("gui.ae2utility.recipe_tree.unknown_input").getString()
                : name;
    }

    private static String formatMergedLeafAmount(MergedLeaf leaf) {
        String sourceText = leaf.sourceAmountText();
        if (sourceText != null && !sourceText.isBlank() && !sourceText.startsWith("x")) {
            return sourceText;
        }
        return "x" + formatCompactCount(Math.max(1, leaf.totalAmount()));
    }

    private static String exactAmountOf(MergedLeaf leaf) {
        String sourceText = leaf.sourceAmountText();
        if (sourceText != null && !sourceText.isBlank() && !sourceText.startsWith("x")) {
            return sourceText;
        }
        return "数量: " + Math.max(1, leaf.totalAmount());
    }

    private String leafSignatureOf(RecipeTreeInputViewModel input) {
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
        return "name#" + displayNameOf(input);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // intentionally empty: super.render() calls this internally again,
        // so we tile the background manually at the start of render().
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (int y = 0; y < this.height; y += BG_TILE_HEIGHT) {
            for (int x = 0; x < this.width; x += BG_TILE_WIDTH) {
                int drawWidth = Math.min(BG_TILE_WIDTH, this.width - x);
                int drawHeight = Math.min(BG_TILE_HEIGHT, this.height - y);
                graphics.blit(BG_TEXTURE, x, y, 0, 0, drawWidth, drawHeight, BG_TILE_WIDTH, BG_TILE_HEIGHT);
            }
        }
        graphics.fill(0, 0, this.width, this.height, 0x16000000);

        graphics.pose().pushPose();
        graphics.pose().translate(panX, panY, 0.0F);
        graphics.pose().scale((float) zoom, (float) zoom, 1.0f);
        renderEdges(graphics);
        graphics.pose().translate(0.0F, 0.0F, 1.0F);
        renderTopMaterials(graphics);
        renderNodes(graphics);
        graphics.pose().popPose();

        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, 10, 12, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.translatable("gui.ae2utility.recipe_tree.overview_hint"), 10, 26, 0xFFDDE6EE, false);
        graphics.drawString(this.font, Component.literal("所需样板数: " + getRequiredPatternCount()), 10, 40, 0xFFEAF4FF, false);

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F);
        renderSelectionPanel(graphics, mouseX, mouseY);
        renderSelectionButtons(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popPose();

        double logicalMouseX = (mouseX - panX) / zoom;
        double logicalMouseY = (mouseY - panY) / zoom;
        renderTooltip(graphics, logicalMouseX, logicalMouseY, mouseX, mouseY);

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 320.0F);
        renderSelectionTooltip(graphics, mouseX, mouseY);
        graphics.pose().popPose();
    }

    private void renderEdges(GuiGraphics graphics) {
        for (Edge edge : edges) {
            int startX = edge.parent().x() + edge.parent().graph().width() / 2;
            int startY = edge.parent().y() + NODE_HEIGHT;
            int endX = edge.child().x() + edge.child().graph().width() / 2;
            int endY = edge.child().y();
            int midY = startY + 12;
            graphics.fill(startX - 1, startY, startX + 2, midY, 0xB0000000);
            graphics.fill(Math.min(startX, endX), midY - 1, Math.max(startX, endX) + 1, midY + 2, 0xB0000000);
            graphics.fill(endX - 1, midY, endX + 2, endY, 0xB0000000);
            graphics.fill(startX, startY, startX + 1, midY, 0xFFFFFFFF);
            graphics.fill(Math.min(startX, endX), midY, Math.max(startX, endX) + 1, midY + 1, 0xFFFFFFFF);
            graphics.fill(endX, midY, endX + 1, endY, 0xFFFFFFFF);
        }
    }

    private void renderNodes(GuiGraphics graphics) {
        for (PositionedNode node : positionedNodes) {
            int x = node.x();
            int y = node.y();
            int width = node.graph().width();
            int border = node.graph().children().isEmpty() ? 0xFFC3CBD3 : 0xFFFFFFFF;
            graphics.fill(x, y, x + width, y + NODE_HEIGHT, 0xCC11161B);
            graphics.fill(x, y, x + width, y + 1, border);
            graphics.fill(x, y + NODE_HEIGHT - 1, x + width, y + NODE_HEIGHT, border);
            graphics.fill(x, y, x + 1, y + NODE_HEIGHT, border);
            graphics.fill(x + width - 1, y, x + width, y + NODE_HEIGHT, border);

            renderIngredientAt(graphics, node.graph().ingredient(), x + 4, y + 5);
            if (!node.graph().amount().isBlank()) {
                graphics.drawString(this.font, Component.literal(node.graph().amount()), x + 24, y + 9, 0xFFD4DEE7, false);
            }
            if (node.graph().machineIcon() != null) {
                renderMachineSlot(graphics, node.graph().machineIcon(), x + width - MACHINE_SLOT_SIZE - 3, y + 4);
            }

            if (showsCollapseButton(node)) {
                int btnX = x + width + 2;
                int btnY = y + 8;
                graphics.fill(btnX, btnY, btnX + 10, btnY + 10, 0xFF4D5962);
                graphics.fill(btnX + 1, btnY + 1, btnX + 9, btnY + 9, 0xFF30373D);
                graphics.drawString(this.font, "-", btnX + 3, btnY + 1, 0xFFFFFFFF, false);
            }
        }
    }

    private void renderTopMaterials(GuiGraphics graphics) {
        topMaterialBounds.clear();
        if (topMaterials == null || topMaterials.isEmpty() || rootNode == null) {
            return;
        }

        int rootCenterX = 0;
        int rootY = 0;
        for (PositionedNode node : positionedNodes) {
            if (node.graph() == rootNode) {
                rootCenterX = node.x() + node.graph().width() / 2;
                rootY = node.y();
                break;
            }
        }

        int gap = 4;
        int totalWidth = -gap;
        int[] widths = new int[topMaterials.size()];
        String[] labels = new String[topMaterials.size()];

        for (int i = 0; i < topMaterials.size(); i++) {
            RequestedIngredient mat = topMaterials.get(i);
            String label = "x" + formatCompactCount(mat.count());
            labels[i] = label;
            int w = 24 + this.font.width(label) + 6;
            widths[i] = w;
            totalWidth += w + gap;
        }

        int startX = rootCenterX - totalWidth / 2;
        int y = rootY - NODE_HEIGHT - 24;
        fillFramedPanel(graphics, startX - 4, y - 4, startX + totalWidth + 4, y + NODE_HEIGHT + 4,
                0xFFFFFFFF, 0xFF4D5962, 0xCC11161B);

        int currentX = startX;
        for (int i = 0; i < topMaterials.size(); i++) {
            RequestedIngredient mat = topMaterials.get(i);
            int width = widths[i];

            graphics.fill(currentX, y, currentX + width, y + NODE_HEIGHT, 0xCC11161B);
            graphics.fill(currentX, y, currentX + width, y + 1, 0xFFFFFFFF);
            graphics.fill(currentX, y + NODE_HEIGHT - 1, currentX + width, y + NODE_HEIGHT, 0xFFFFFFFF);
            graphics.fill(currentX, y, currentX + 1, y + NODE_HEIGHT, 0xFFFFFFFF);
            graphics.fill(currentX + width - 1, y, currentX + width, y + NODE_HEIGHT, 0xFFFFFFFF);

            ItemStack itemStack = ItemStack.EMPTY;
            if (!mat.alternatives().isEmpty()) {
                itemStack = mat.alternatives().get(0).copyWithCount(mat.count());
            }
            if (!itemStack.isEmpty()) {
                graphics.renderItem(itemStack, currentX + 4, y + 5);
                graphics.drawString(this.font, Component.literal(labels[i]), currentX + 24, y + 9, 0xFFD4DEE7, false);
                topMaterialBounds.add(new TopMaterialBounds(mat, currentX, y, width, NODE_HEIGHT));
            }

            currentX += width + gap;
        }
    }

    private void renderMachineSlot(GuiGraphics graphics, IDrawable icon, int x, int y) {
        graphics.fill(x, y, x + MACHINE_SLOT_SIZE, y + MACHINE_SLOT_SIZE, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + MACHINE_SLOT_SIZE - 1, y + MACHINE_SLOT_SIZE - 1, 0xFFBFC9D2);
        graphics.fill(x + 2, y + 2, x + MACHINE_SLOT_SIZE - 2, y + MACHINE_SLOT_SIZE - 2, 0xFF2A3137);
        icon.draw(graphics, x + 1, y + 1);
    }

    private void renderTooltip(GuiGraphics graphics, double logicalMouseX, double logicalMouseY, int mouseX, int mouseY) {
        if (toggleExistingPatternButton.visible && isPointInsideButton(toggleExistingPatternButton, mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    List.of(Component.literal(context.disableExistingPatternExpansion()
                            ? "已禁用展开 ME 网络已有样板"
                            : "已允许展开 ME 网络已有样板")),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        for (TopMaterialBounds bounds : topMaterialBounds) {
            if (!bounds.contains(logicalMouseX, logicalMouseY) || bounds.material().alternatives().isEmpty()) {
                continue;
            }
            ItemStack stack = bounds.material().alternatives().get(0);
            List<Component> lines = new ArrayList<>();
            lines.add(stack.getHoverName());
            lines.add(Component.literal("数量: " + bounds.material().count()).withStyle(ChatFormatting.GRAY));
            if (hasExistingPatternForRequestedIngredient(bounds.material())) {
                lines.add(Component.literal("ME 网络已有样板").withStyle(ChatFormatting.RED));
            }
            graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
            return;
        }

        for (PositionedNode node : positionedNodes) {
            int x = node.x();
            int y = node.y();
            int width = node.graph().width();
            int collapseX = x + width + 2;
            int collapseY = y + 8;
            if (showsCollapseButton(node)
                    && logicalMouseX >= collapseX && logicalMouseX <= collapseX + 10
                    && logicalMouseY >= collapseY && logicalMouseY <= collapseY + 10) {
                graphics.renderTooltip(this.font, List.of(Component.literal("折叠分支")), Optional.empty(), mouseX, mouseY);
                return;
            }
            if (logicalMouseX < x || logicalMouseX > x + width || logicalMouseY < y || logicalMouseY > y + NODE_HEIGHT) {
                continue;
            }
            if (node.graph().mergedLeaf() != null && shouldBlockExpansion(node.graph().mergedLeaf())) {
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable("gui.ae2utility.recipe_tree.pattern_exists").withStyle(ChatFormatting.RED)),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
            int machineX = x + width - MACHINE_SLOT_SIZE - 3;
            if (node.graph().machineIcon() != null
                    && logicalMouseX >= machineX && logicalMouseX <= machineX + MACHINE_SLOT_SIZE
                    && logicalMouseY >= y + 4 && logicalMouseY <= y + 4 + MACHINE_SLOT_SIZE) {
                List<Component> lines = new ArrayList<>();
                lines.add(node.graph().titleComponent());
                if (node.graph().machineName() != null && !node.graph().machineName().getString().isBlank()) {
                    lines.add(node.graph().machineName().copy().withStyle(ChatFormatting.GRAY));
                }
                graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
                return;
            }
            if (logicalMouseX >= x + 2 && logicalMouseX <= x + 22) {
                List<Component> lines = new ArrayList<>();
                lines.add(node.graph().titleComponent());
                if (!node.graph().exactAmount().isBlank()) {
                    lines.add(Component.literal(node.graph().exactAmount()).withStyle(ChatFormatting.GRAY));
                }
                if (node.graph().recipeNode() != null && node.graph().machineName() != null
                        && !node.graph().machineName().getString().isBlank()) {
                    lines.add(node.graph().machineName().copy().withStyle(ChatFormatting.GRAY));
                }
                graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
            }
            return;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pendingSelection != null) {
            for (CandidateCardBounds card : candidateCards) {
                if (card.contains(mouseX, mouseY)) {
                    selectCandidate(card.recipe());
                    return true;
                }
            }
        }
        if (button == 0) {
            double logicalMouseX = (mouseX - panX) / zoom;
            double logicalMouseY = (mouseY - panY) / zoom;
            TopMaterialBounds materialTarget = findTopMaterialAt(logicalMouseX, logicalMouseY);
            if (materialTarget != null) {
                jumpToMaterial(materialTarget.material());
                return true;
            }
            PositionedNode collapseTarget = findCollapseButtonAt(logicalMouseX, logicalMouseY);
            if (collapseTarget != null) {
                collapseNode(collapseTarget);
                closeSelection();
                rebuildLayout();
                return true;
            }
            PositionedNode clicked = findNodeAt(logicalMouseX, logicalMouseY);
            if (clicked != null) {
                if (clicked.graph().recipeNode() != null) {
                    openSelection(clicked.graph().recipeNode());
                    return true;
                }
                if (clicked.graph().mergedLeaf() != null) {
                    if (shouldBlockExpansion(clicked.graph().mergedLeaf())) {
                        return true;
                    }
                    openSelection(clicked.graph().mergedLeaf());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private @Nullable PositionedNode findNodeAt(double logicalMouseX, double logicalMouseY) {
        for (PositionedNode node : positionedNodes) {
            if (logicalMouseX >= node.x() && logicalMouseX <= node.x() + node.graph().width()
                    && logicalMouseY >= node.y() && logicalMouseY <= node.y() + NODE_HEIGHT) {
                return node;
            }
        }
        return null;
    }

    private @Nullable TopMaterialBounds findTopMaterialAt(double logicalMouseX, double logicalMouseY) {
        for (TopMaterialBounds bounds : topMaterialBounds) {
            if (bounds.contains(logicalMouseX, logicalMouseY)) {
                return bounds;
            }
        }
        return null;
    }

    private @Nullable PositionedNode findCollapseButtonAt(double logicalMouseX, double logicalMouseY) {
        for (PositionedNode node : positionedNodes) {
            if (!showsCollapseButton(node)) {
                continue;
            }
            int x = node.x() + node.graph().width() + 2;
            int y = node.y() + 8;
            if (logicalMouseX >= x && logicalMouseX <= x + 10
                    && logicalMouseY >= y && logicalMouseY <= y + 10) {
                return node;
            }
        }
        return null;
    }

    private boolean showsCollapseButton(PositionedNode node) {
        return !node.graph().children().isEmpty()
                && node.graph().recipeNode() != null
                && node.graph().recipeNode().parent() != null;
    }

    private boolean shouldBlockExpansion(MergedLeaf leaf) {
        return context.disableExistingPatternExpansion() && hasExistingPatternForLeaf(leaf);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (pendingSelection != null && isInsideSelectionViewport(mouseX, mouseY)
                && selectionContentHeight > getSelectionViewportHeight()) {
            selectionScroll -= (int) (scrollY * SCROLL_STEP);
            clampSelectionScroll();
            return true;
        }
        if (Screen.hasControlDown()) {
            double oldZoom = zoom;
            zoom = Math.max(0.2, Math.min(4.0, zoom + scrollY * 0.1));
            double logicalX = (mouseX - panX) / oldZoom;
            double logicalY = (mouseY - panY) / oldZoom;
            panX = mouseX - logicalX * zoom;
            panY = mouseY - logicalY * zoom;
            return true;
        }
        if (Screen.hasShiftDown()) {
            panX += scrollY * 18;
        } else {
            panY += scrollY * 18;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && pendingSelection == null) {
            panX += dragX;
            panY += dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(returnScreen);
    }

    private void openSelection(RecipeTreeNodeViewModel target) {
        ITypedIngredient<?> ingredient = target.recipe().primaryOutputIngredient();
        if (ingredient == null) {
            return;
        }
        List<DisplayOption> options = List.of(new DisplayOption(ingredient, target.recipe().title().getString(), target.recipe().primaryOutput()));
        RecipeTreeInputViewModel syntheticInput = new RecipeTreeInputViewModel(null, options, target.recipe().primaryOutputCount(),
                target.recipe().primaryOutputCount() > 1 ? "x" + target.recipe().primaryOutputCount() : "");
        List<RecipeTreeRecipeViewModel> candidates = RecipeTreeExpansionResolver.resolveCandidates(syntheticInput).stream()
                .filter(candidate -> !containsRecipeInAncestors(target.parent(), candidate))
                .toList();
        this.pendingSelection = new PendingSelection(target, null, candidates, groupCandidates(candidates));
        this.selectionScroll = 0;
        this.selectionGroupIndex = 0;
        clampSelectionScroll();
        updateSelectionButtons();
    }

    private void openSelection(MergedLeaf target) {
        RecipeTreeInputViewModel representative = target.representative();
        List<RecipeTreeRecipeViewModel> candidates = RecipeTreeExpansionResolver.resolveCandidates(representative).stream()
                .filter(candidate -> !target.parentNode().containsRecipe(candidate))
                .toList();
        this.pendingSelection = new PendingSelection(null, target, candidates, groupCandidates(candidates));
        this.selectionScroll = 0;
        this.selectionGroupIndex = 0;
        clampSelectionScroll();
        updateSelectionButtons();
    }

    private boolean containsRecipeInAncestors(@Nullable RecipeTreeNodeViewModel node, RecipeTreeRecipeViewModel candidate) {
        for (RecipeTreeNodeViewModel cursor = node; cursor != null; cursor = cursor.parent()) {
            if (cursor.recipe().sameRecipeAs(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void selectCandidate(RecipeTreeRecipeViewModel selected) {
        if (pendingSelection == null) {
            return;
        }
        if (pendingSelection.targetNode() != null) {
            RecipeTreeNodeViewModel target = pendingSelection.targetNode();
            target.setRecipe(selected);
            autoApplyRememberedChildren(target);
        } else if (pendingSelection.targetLeaf() != null) {
            applyLeafSelection(pendingSelection.targetLeaf(), selected);
        }
        closeSelection();
        rebuildLayout();
    }

    private void applyLeafSelection(MergedLeaf target, RecipeTreeRecipeViewModel selected) {
        context.rememberSelection(signatureOf(target.representative()), selected);
        RecipeTreeNodeViewModel childNode = new RecipeTreeNodeViewModel(selected, target.parentNode());
        for (RecipeTreeInputViewModel input : target.members()) {
            input.setChild(childNode);
        }
        autoApplyRememberedChildren(childNode);
    }

    private void autoApplyRememberedChildren(RecipeTreeNodeViewModel parent) {
        for (RecipeTreeInputViewModel input : parent.recipe().inputs()) {
            if (input.child() != null) {
                continue;
            }
            String signature = signatureOf(input);
            RecipeTreeRecipeViewModel remembered = context.getRememberedSelection(signature);
            if (remembered == null || parent.containsRecipe(remembered)) {
                continue;
            }
            input.setChild(new RecipeTreeNodeViewModel(remembered, parent));
        }
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
        return "name#" + displayNameOf(input);
    }

    private String signatureOf(ITypedIngredient<?> ingredient) {
        if (ingredient == null) {
            return "null";
        }
        IIngredientManager ingredientManager = getIngredientManager();
        if (ingredientManager != null) {
            return signatureOfTypedIngredient(ingredientManager, ingredient);
        }
        Object raw = ingredient.getIngredient();
        return "typed#" + raw.getClass().getName() + "#" + raw;
    }

    private String signatureOf(RecipeTreeNodeViewModel node) {
        ITypedIngredient<?> primaryOutput = node.recipe().primaryOutputIngredient();
        if (primaryOutput != null) {
            return signatureOf(primaryOutput);
        }
        return "node#" + node.recipe().title().getString();
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

    private void closeSelection() {
        this.pendingSelection = null;
        this.selectionScroll = 0;
        this.selectionContentHeight = 0;
        this.selectionGroupIndex = 0;
        this.candidateCards.clear();
        this.candidateSlots.clear();
        updateSelectionButtons();
    }

    private void clearPendingSelection() {
        if (pendingSelection == null) {
            return;
        }

        if (pendingSelection.targetNode() != null) {
            RecipeTreeNodeViewModel targetNode = pendingSelection.targetNode();
            context.forgetSelection(signatureOf(targetNode));
            targetNode.recipe().inputs().forEach(input -> input.setChild(null));
        } else if (pendingSelection.targetLeaf() != null) {
            MergedLeaf targetLeaf = pendingSelection.targetLeaf();
            context.forgetSelection(signatureOf(targetLeaf.representative()));
            targetLeaf.members().forEach(input -> input.setChild(null));
        }
    }

    private void collapseNode(PositionedNode positionedNode) {
        RecipeTreeNodeViewModel node = positionedNode.graph().recipeNode();
        if (node == null || node.parent() == null) {
            return;
        }
        for (RecipeTreeInputViewModel input : node.parent().recipe().inputs()) {
            if (input.child() == node) {
                context.forgetSelection(signatureOf(input));
                input.setChild(null);
            }
        }
    }

    private void jumpToMaterial(RequestedIngredient material) {
        String targetSignature = signatureOf(material);
        PositionedNode matchedLeaf = null;
        double logicalCenterX = (this.width * 0.5D - panX) / zoom;
        double logicalCenterY = (this.height * 0.5D - panY) / zoom;
        double bestDistance = Double.MAX_VALUE;
        for (PositionedNode node : positionedNodes) {
            MergedLeaf leaf = node.graph().mergedLeaf();
            if (leaf == null || !signatureOf(leaf.representative()).equals(targetSignature)) {
                continue;
            }
            double centerX = node.x() + node.graph().width() / 2.0D;
            double centerY = node.y() + NODE_HEIGHT / 2.0D;
            double distance = Math.pow(centerX - logicalCenterX, 2) + Math.pow(centerY - logicalCenterY, 2);
            if (distance < bestDistance) {
                bestDistance = distance;
                matchedLeaf = node;
            }
        }
        if (matchedLeaf == null) {
            return;
        }
        panX = this.width * 0.5D - (matchedLeaf.x() + matchedLeaf.graph().width() / 2.0D) * zoom;
        panY = this.height * 0.45D - (matchedLeaf.y() + NODE_HEIGHT / 2.0D) * zoom;
    }

    private boolean hasExistingPatternForLeaf(MergedLeaf leaf) {
        return hasExistingPatternForRequestedIngredient(leaf.representative().requestedIngredient());
    }

    private boolean hasExistingPatternForRequestedIngredient(@Nullable RequestedIngredient ingredient) {
        if (ingredient == null) {
            return false;
        }
        for (ItemStack alternative : ingredient.alternatives()) {
            if (CraftableStateCache.isCraftable(GenericIngredientUtil.toAEKey(alternative))) {
                return true;
            }
        }
        return false;
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

    private void updateSelectionButtons() {
        boolean visible = pendingSelection != null;
        cancelSelectionButton.visible = visible;
        cancelSelectionButton.active = visible;
        toggleExistingPatternButton.visible = !visible;
        toggleExistingPatternButton.active = !visible;
        prevGroupButton.visible = visible && pendingSelection != null && pendingSelection.groups().size() > 1;
        prevGroupButton.active = prevGroupButton.visible && selectionGroupIndex > 0;
        nextGroupButton.visible = visible && pendingSelection != null && pendingSelection.groups().size() > 1;
        nextGroupButton.active = nextGroupButton.visible && selectionGroupIndex < pendingSelection.groups().size() - 1;
        backButton.visible = !visible;
        backButton.active = !visible;
        backButton.setX(this.width - 68);
        backButton.setY(10);
        toggleExistingPatternButton.setX(this.width - 166);
        toggleExistingPatternButton.setY(10);
        syncToggleExistingPatternButton();
        if (visible) {
            cancelSelectionButton.setX(getSelectionPanelX() + SELECTION_PANEL_WIDTH - 61);
            cancelSelectionButton.setY(this.height - 31);
            prevGroupButton.setX(getSelectionPanelX() + 6);
            prevGroupButton.setY(32);
            nextGroupButton.setX(getSelectionPanelX() + SELECTION_PANEL_WIDTH - 24);
            nextGroupButton.setY(32);
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

    private int getSelectionPanelX() {
        return this.width - SELECTION_PANEL_WIDTH - 12;
    }

    private int getSelectionViewportTop() {
        return 62;
    }

    private int getSelectionViewportBottom() {
        return this.height - 32;
    }

    private int getSelectionViewportHeight() {
        return getSelectionViewportBottom() - getSelectionViewportTop();
    }

    private boolean isInsideSelectionViewport(double mouseX, double mouseY) {
        int x = getSelectionPanelX() + 4;
        return mouseX >= x && mouseX <= x + SELECTION_PANEL_WIDTH - 8
                && mouseY >= getSelectionViewportTop() && mouseY <= getSelectionViewportBottom();
    }

    private void clampSelectionScroll() {
        int maxScroll = Math.max(0, selectionContentHeight - getSelectionViewportHeight());
        selectionScroll = Math.max(0, Math.min(maxScroll, selectionScroll));
    }

    private void renderSelectionPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        candidateCards.clear();
        candidateSlots.clear();
        if (pendingSelection == null) {
            return;
        }

        int panelX = getSelectionPanelX();
        int panelY = 8;
        fillFramedPanel(graphics, panelX, panelY, panelX + SELECTION_PANEL_WIDTH, this.height - 8,
                0xFF30373D, 0xFF4D5962, 0xFFE8EEF2);
        graphics.fill(panelX + 4, panelY + 18, panelX + SELECTION_PANEL_WIDTH - 4, panelY + 48, 0xFF46515A);
        graphics.drawString(this.font,
                trimToWidth(Component.translatable("gui.ae2utility.recipe_tree.choose_recipe_for",
                        pendingSelectionTitle().getString()), SELECTION_PANEL_WIDTH - 14),
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
        selectionContentHeight = currentGroup.recipes().size() * (SELECTION_CARD_HEIGHT + SELECTION_CARD_GAP) - SELECTION_CARD_GAP;
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

    private void renderSelectionTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
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

    private void renderCandidateCard(GuiGraphics graphics, RecipeTreeRecipeViewModel recipe, int x, int y, int width, boolean hovered) {
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
            graphics.drawString(this.font, Component.literal("+" + (inputs.size() - 6)), inputX + 38, inputY + 36, 0xFF566168, false);
        }

        graphics.drawString(this.font, Component.literal(">"), x + 66, y + 46, 0xFF4A565F, false);
        int outputX = x + width - 24;
        int outputY = y + 40;
        ITypedIngredient<?> output = recipe.primaryOutputIngredient();
        renderTypedSlot(graphics, outputX, outputY, output);
        candidateSlots.add(new CandidateSlotBounds(output, outputX, outputY, 18, 18));
    }

    private @Nullable SelectionGroup getCurrentSelectionGroup() {
        if (pendingSelection == null || pendingSelection.groups().isEmpty()) {
            return null;
        }
        int index = Math.max(0, Math.min(pendingSelection.groups().size() - 1, selectionGroupIndex));
        return pendingSelection.groups().get(index);
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

    private Component pendingSelectionTitle() {
        if (pendingSelection == null) {
            return Component.empty();
        }
        if (pendingSelection.targetNode() != null) {
            return pendingSelection.targetNode().recipe().title();
        }
        if (pendingSelection.targetLeaf() != null) {
            return Component.literal(pendingSelection.targetLeaf().title());
        }
        return Component.empty();
    }

    private Component trimToWidth(Component text, int maxWidth) {
        return Component.literal(this.font.substrByWidth(text, Math.max(8, maxWidth)).getString());
    }

    private void renderTypedSlot(GuiGraphics graphics, int x, int y, @Nullable ITypedIngredient<?> ingredient) {
        ItemStack itemStack = extractItemStack(ingredient);
        renderSlot(graphics, x, y, itemStack);
        if (itemStack.isEmpty() && ingredient != null) {
            renderIngredientAt(graphics, ingredient, x + 1, y + 1);
        }
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

    private void renderIngredientAt(GuiGraphics graphics, @Nullable ITypedIngredient<?> ingredient, int x, int y) {
        if (ingredient == null) {
            return;
        }
        ItemStack itemStack = extractItemStack(ingredient);
        if (!itemStack.isEmpty()) {
            graphics.renderItem(itemStack, x, y);
            return;
        }
        IIngredientManager ingredientManager = getIngredientManager();
        if (ingredientManager == null) {
            return;
        }
        renderJeiIngredientTyped(graphics, ingredientManager, ingredient, x, y);
    }

    private static ItemStack extractItemStack(@Nullable ITypedIngredient<?> ingredient) {
        return ingredient == null
                ? ItemStack.EMPTY
                : ingredient.getIngredient(mezz.jei.api.constants.VanillaTypes.ITEM_STACK).map(ItemStack::copy).orElse(ItemStack.EMPTY);
    }

    private @Nullable IIngredientManager getIngredientManager() {
        var runtime = com.lhy.ae2utility.jei.Ae2UtilityJeiPlugin.getJeiRuntime();
        return runtime == null ? null : runtime.getIngredientManager();
    }

    private <T> void renderJeiIngredientTyped(GuiGraphics graphics, IIngredientManager ingredientManager, ITypedIngredient<?> ingredient,
            int x, int y) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> typed = (ITypedIngredient<T>) ingredient;
        IIngredientRenderer<T> renderer = ingredientManager.getIngredientRenderer(typed.getType());
        renderer.render(graphics, typed.getIngredient(), x, y);
    }

    private void syncToggleExistingPatternButton() {
        toggleExistingPatternButton.setMessage(Component.literal(
                context.disableExistingPatternExpansion() ? "已有样板:禁" : "已有样板:开"));
    }

    private int getRequiredPatternCount() {
        int count = 0;
        for (RecipeTreeRecipeViewModel recipe : context.collectSelectedRecipes()) {
            if (!hasExistingPatternForOutput(recipe) && recipe.primaryOutputIngredient() != null) {
                count++;
            }
        }
        return count;
    }

    private boolean hasExistingPatternForOutput(RecipeTreeRecipeViewModel recipe) {
        return recipe != null && recipe.primaryOutputIngredient() != null
                && CraftableStateCache.isCraftable(GenericIngredientUtil.toAEKey(recipe.primaryOutputIngredient().getIngredient()));
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

    private static boolean isPointInsideButton(Button button, double mouseX, double mouseY) {
        return button != null && button.visible
                && mouseX >= button.getX() && mouseX <= button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY <= button.getY() + button.getHeight();
    }

    private void fillFramedPanel(GuiGraphics graphics, int left, int top, int right, int bottom, int border, int middle, int inner) {
        graphics.fill(left, top, right, bottom, border);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, middle);
        graphics.fill(left + 3, top + 3, right - 3, bottom - 3, inner);
    }

    private static int safeMultiply(int left, int right) {
        long value = (long) left * (long) right;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, value));
    }

    private static int safeAdd(int left, int right) {
        long value = (long) left + (long) right;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, value));
    }

    private static int ceilDiv(int numerator, int denominator) {
        if (denominator <= 0) {
            return numerator;
        }
        return (numerator + denominator - 1) / denominator;
    }

    private record GraphNode(@Nullable ITypedIngredient<?> ingredient, String title, String amount, String exactAmount,
            @Nullable RecipeTreeNodeViewModel recipeNode, @Nullable IDrawable machineIcon,
            @Nullable Component machineName, @Nullable MergedLeaf mergedLeaf, List<GraphNode> children, int width) {
        private Component titleComponent() {
            return Component.literal(title);
        }
    }

    private record MergedLeaf(@Nullable ITypedIngredient<?> ingredient, String title, int totalAmount, String sourceAmountText,
            RecipeTreeNodeViewModel parentNode, List<RecipeTreeInputViewModel> members) {
        private MergedLeaf withAddedAmount(int addedAmount, RecipeTreeInputViewModel member) {
            List<RecipeTreeInputViewModel> nextMembers = new ArrayList<>(members);
            nextMembers.add(member);
            return new MergedLeaf(ingredient, title, safeAdd(totalAmount, Math.max(1, addedAmount)), sourceAmountText, parentNode,
                    List.copyOf(nextMembers));
        }

        private RecipeTreeInputViewModel representative() {
            return members.get(0);
        }
    }

    private record PositionedNode(GraphNode graph, int x, int y) {
    }

    private record Edge(PositionedNode parent, PositionedNode child) {
    }

    private record PendingSelection(@Nullable RecipeTreeNodeViewModel targetNode, @Nullable MergedLeaf targetLeaf,
            List<RecipeTreeRecipeViewModel> candidates,
            List<SelectionGroup> groups) {
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

    private record TopMaterialBounds(RequestedIngredient material, int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private record SelectionGroup(Component label, List<RecipeTreeRecipeViewModel> recipes) {
    }
}