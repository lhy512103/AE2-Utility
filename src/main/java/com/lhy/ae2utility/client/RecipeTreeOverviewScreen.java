package com.lhy.ae2utility.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.lhy.ae2utility.client.recipe_tree.RecipeTreeInputViewModel;
import com.lhy.ae2utility.client.recipe_tree.RecipeTreeInputViewModel.DisplayOption;
import com.lhy.ae2utility.client.recipe_tree.RecipeTreeNodeViewModel;
import com.lhy.ae2utility.client.recipe_tree.RecipeTreeRecipeViewModel;
import com.lhy.ae2utility.client.recipe_tree.RecipeTreeRootContext;
import com.lhy.ae2utility.compat.EaepCompat;
import com.lhy.ae2utility.client.RemoteEncodeRules;
import com.lhy.ae2utility.client.jei.BlankPatternClientPrecheck;
import com.lhy.ae2utility.debug.RecipeTreePerfDebug;
import com.lhy.ae2utility.jei.Ae2UtilityJeiPlugin;
import com.lhy.ae2utility.jei.BulkEncodeSessions;
import com.lhy.ae2utility.jei.CraftableStateCache;
import com.lhy.ae2utility.jei.JeiPatternSubstitutionUi;
import com.lhy.ae2utility.jei.RecipeTreeJeiLookup;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.PullRecipeInputsPacket.RequestedIngredient;
import com.lhy.ae2utility.util.GenericIngredientUtil;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.neoforge.NeoForgeTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.network.PacketDistributor;

import com.mojang.blaze3d.systems.RenderSystem;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;

public class RecipeTreeOverviewScreen extends Screen implements RecipeTreeJeiTransferTarget {
    private static final int NODE_HEIGHT = 26;
    private static final int LEVEL_GAP = 44;
    private static final int LEAF_GAP = 16;
    private static final int AUTO_EXPAND_STEPS_PER_TICK = 1;
    private static final int VISIBILITY_MARGIN = 32;
    private static final int BG_TILE_WIDTH = 256;
    private static final int BG_TILE_HEIGHT = 256;
    private static final int MACHINE_SLOT_SIZE = 18;
    /** 与样板编码终端/JEI 开关同源图标（8×8 纹理缩放绘制） */
    private static final int SUBSTITUTION_ICON_SRC = 8;
    private static final int SUBSTITUTION_ICON_DST = 16;
    private static final int TOP_MATERIALS_OFFSET = 48;
    private static final ResourceLocation BG_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("ae2utility", "textures/gui/recipe_tree_bg.png");

    private final RecipeTreeRootContext context;
    private final Screen returnScreen;
    private final List<PositionedNode> positionedNodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private final List<TopMaterialBounds> topMaterialBounds = new ArrayList<>();
    private final List<AlternativeButtonBounds> alternativeButtonBounds = new ArrayList<>();
    private final List<AlternativeOptionBounds> alternativeOptionBounds = new ArrayList<>();
    private final List<LayerMaterialBounds> layerMaterialBounds = new ArrayList<>();
    private List<RequestedIngredient> topMaterials = List.of();
    private List<LayerRow> mergedLayerRows = List.of();

    private GraphNode rootNode;
    private Button backButton;
    private Button toggleExistingPatternButton;
    private Button autoUniqueRecipeButton;
    private Button autoMergeButton;
    private Button encodeButton;
    private Button uploadButton;
    private double panX;
    private double panY;
    private double zoom = 1.0;
    private boolean initializedPan;
    private int alternativeScroll;
    private int cachedRequiredPatternCount;
    private Component cachedRequiredPatternsTitleLine = Component.empty();
    private boolean autoMergeSameMaterials = true;
    /** 关闭后不演算树上用量、所需样板数，也不查询 ME 中是否已有样板（减轻卡顿） */
    private boolean computeRecipeQuantities = true;
    private Button computeQuantitiesButton;
    /** 开启时自动展开「JEI 中仅有一条可编码配方」的未解析分支；若「已有样板:禁」则跳过网络中已有样板的分支（见 shouldBlockExpansion） */
    private boolean autoExpandUniqueEncodableRecipe = false;
    private boolean suppressAutoExpandUniqueRecipePass;
    private boolean autoExpandUniqueSearchPending;
    private final Map<String, Optional<RecipeTreeRecipeViewModel>> autoExpandUniqueCandidateCache = new HashMap<>();
    private @Nullable PendingJeiSelection pendingJeiSelection;
    private @Nullable PendingAlternativeSelection pendingAlternativeSelection;
    private int lastRenderedNodeCount;
    private int lastRenderedEdgeCount;
    private int lastRenderedLayerCount;
    private int lastRenderedLayerMaterialCount;
    private int lastRenderedTopMaterialCount;
    private @Nullable MergedBuildStats lastMergedBuildStats;

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
                    if (context.disableExistingPatternExpansion()) {
                        collapseExpandedExistingPatternNodes(context.root());
                    }
                    syncToggleExistingPatternButton();
                    rebuildLayout();
                }).bounds(this.width - 212, 10, 68, 20).build();
        this.autoUniqueRecipeButton = Button.builder(Component.empty(),
                btn -> {
                    autoExpandUniqueEncodableRecipe = !autoExpandUniqueEncodableRecipe;
                    markAutoExpandUniqueDirty();
                    syncAutoUniqueRecipeButton();
                    rebuildLayout();
                }).bounds(this.width - 284, 10, 68, 20).build();
        this.computeQuantitiesButton = Button.builder(Component.empty(),
                btn -> {
                    computeRecipeQuantities = !computeRecipeQuantities;
                    syncComputeQuantitiesButton();
                    refreshRenderedProjection();
                }).bounds(this.width - 356, 10, 68, 20).build();
        this.autoMergeButton = Button.builder(Component.empty(),
                btn -> {
                    autoMergeSameMaterials = !autoMergeSameMaterials;
                    syncAutoMergeButton();
                    rebuildLayout();
                }).bounds(this.width - 140, 10, 68, 20).build();
        this.encodeButton = Button.builder(Component.translatable("gui.ae2utility.recipe_tree.encode"),
                btn -> encodePatterns()).bounds(this.width / 2 - 62, this.height - 26, 60, 22).build();
        this.uploadButton = Button.builder(Component.translatable("gui.ae2utility.recipe_tree.upload"),
                btn -> uploadPatterns()).bounds(this.width / 2 + 4, this.height - 26, 60, 22).build();

        this.addRenderableWidget(backButton);
        this.addRenderableWidget(computeQuantitiesButton);
        this.addRenderableWidget(toggleExistingPatternButton);
        this.addRenderableWidget(autoUniqueRecipeButton);
        this.addRenderableWidget(autoMergeButton);
        this.addRenderableWidget(encodeButton);
        this.addRenderableWidget(uploadButton);
        syncComputeQuantitiesButton();
        syncToggleExistingPatternButton();
        syncAutoUniqueRecipeButton();
        syncAutoMergeButton();
        updateSelectionButtons();
        rebuildLayout();
    }

    @Override
    public void tick() {
        super.tick();
        if (CraftableStateCache.pollRecipeTreeOverlayCachesStale()) {
            refreshCraftableDependentCaches();
        }
        processAutoExpandUniqueRecipeSteps(AUTO_EXPAND_STEPS_PER_TICK);
    }

    private void rebuildLayout() {
        long startedAt = RecipeTreePerfDebug.begin();
        rebuildLayoutStructureCore();
        refreshLayoutDerivedCaches();
        markAutoExpandUniqueDirty();
        RecipeTreePerfDebug.logPhase("rebuild_layout", startedAt,
                "merge={} qty={} autoUnique={} nodes={} edges={} rows={} topMaterials={}",
                autoMergeSameMaterials, computeRecipeQuantities, autoExpandUniqueEncodableRecipe,
                positionedNodes.size(), edges.size(), mergedLayerRows.size(), topMaterials.size());
    }

    /**
     * 数量/提示类开关变化时，只重建渲染投影，不再次跑自动展开流程。
     */
    private void refreshRenderedProjection() {
        long startedAt = RecipeTreePerfDebug.begin();
        rebuildLayoutStructureCore();
        refreshLayoutDerivedCaches();
        RecipeTreePerfDebug.logPhase("refresh_projection", startedAt,
                "merge={} qty={} nodes={} edges={} rows={} topMaterials={}",
                autoMergeSameMaterials, computeRecipeQuantities,
                positionedNodes.size(), edges.size(), mergedLayerRows.size(), topMaterials.size());
    }

    private void refreshLayoutDerivedCaches() {
        long startedAt = RecipeTreePerfDebug.begin();
        List<RecipeTreeRecipeViewModel> selectedRecipes = context.collectSelectedRecipes();
        if (computeRecipeQuantities) {
            cachedRequiredPatternCount = computeRequiredPatternCountUncached(selectedRecipes);
        }
        rebuildRequiredPatternsTitleComponent();
        RecipeTreePerfDebug.logPhase("refresh_derived", startedAt,
                "qty={} requiredPatterns={} selectedRecipes={}",
                computeRecipeQuantities, cachedRequiredPatternCount, selectedRecipes.size());
    }

    private void rebuildRequiredPatternsTitleComponent() {
        if (!computeRecipeQuantities) {
            cachedRequiredPatternsTitleLine = Component.translatable("gui.ae2utility.recipe_tree.overview_required_patterns_skipped");
        } else {
            cachedRequiredPatternsTitleLine =
                    Component.translatable("gui.ae2utility.recipe_tree.overview_required_patterns", cachedRequiredPatternCount);
        }
    }

    private void refreshCraftableDependentCaches() {
        long startedAt = RecipeTreePerfDebug.begin();
        if (!computeRecipeQuantities) {
            RecipeTreePerfDebug.logPhase("refresh_craftable_dependent_skip", startedAt, "qty=false");
            return;
        }
        cachedRequiredPatternCount = computeRequiredPatternCountUncached(context.collectSelectedRecipes());
        rebuildRequiredPatternsTitleComponent();
        if (!autoMergeSameMaterials || mergedLayerRows.isEmpty()) {
            RecipeTreePerfDebug.logPhase("refresh_craftable_dependent", startedAt,
                    "merge={} rows={} requiredPatterns={}",
                    autoMergeSameMaterials, mergedLayerRows.size(), cachedRequiredPatternCount);
            return;
        }
        List<LayerRow> rebuilt = new ArrayList<>(mergedLayerRows.size());
        int changedHints = 0;
        for (LayerRow row : mergedLayerRows) {
            List<LayerMaterial> mats = new ArrayList<>(row.materials().size());
            for (LayerMaterial m : row.materials()) {
                boolean hint = computeLayerMaterialShowsPatternHint(m);
                if (m.showsPatternHint() != hint) {
                    changedHints++;
                    mats.add(m.withShowsPatternHint(hint));
                } else {
                    mats.add(m);
                }
            }
            rebuilt.add(new LayerRow(row.depth(), List.copyOf(mats)));
        }
        mergedLayerRows = List.copyOf(rebuilt);
        RecipeTreePerfDebug.logPhase("refresh_craftable_dependent", startedAt,
                "merge={} rows={} changedHints={} requiredPatterns={}",
                autoMergeSameMaterials, mergedLayerRows.size(), changedHints, cachedRequiredPatternCount);
    }

    private void rebuildLayoutStructureCore() {
        long startedAt = RecipeTreePerfDebug.begin();
        long structureBuildStartedAt = RecipeTreePerfDebug.begin();
        if (autoMergeSameMaterials) {
            this.rootNode = null;
            this.mergedLayerRows = buildMergedLayerRows(context.root(), 1);
            if (lastMergedBuildStats != null) {
                RecipeTreePerfDebug.logPhase("build_merged_rows", structureBuildStartedAt,
                        "calls={} recipeNodeAdds={} leafAdds={} aggregatedChildLinks={} rows={} rowMaterials={}",
                        lastMergedBuildStats.collectCalls, lastMergedBuildStats.recipeNodeAdds,
                        lastMergedBuildStats.leafAdds, lastMergedBuildStats.aggregatedChildLinks,
                        lastMergedBuildStats.layerRows, lastMergedBuildStats.layerMaterials);
            }
        } else {
            this.rootNode = buildGraph(context.root(), 1);
            this.mergedLayerRows = List.of();
            lastMergedBuildStats = null;
            RecipeTreePerfDebug.logPhase("build_graph_tree", structureBuildStartedAt,
                    "nodes={} edges={}", positionedNodes.size(), edges.size());
        }
        long topMaterialsStartedAt = RecipeTreePerfDebug.begin();
        this.topMaterials = context.collectRequestedIngredients();
        RecipeTreePerfDebug.logPhase("collect_top_materials", topMaterialsStartedAt,
                "count={}", topMaterials.size());
        positionedNodes.clear();
        edges.clear();
        layerMaterialBounds.clear();
        if (autoMergeSameMaterials) {
            if (!initializedPan) {
                int contentWidth = computeMergedLayerContentWidth();
                int contentHeight = computeMergedLayerContentHeight();
                panX = this.width * 0.5D - contentWidth / 2.0D;
                panY = 48D - contentHeight / 2.0D + TOP_MATERIALS_OFFSET;
                initializedPan = true;
            }
        } else {
            PositionedNode root = layoutNode(rootNode, 0, 36);
            if (!initializedPan) {
                panX = this.width * 0.5D - (root.x() + rootNode.width() / 2.0D);
                panY = 48D - root.y() + TOP_MATERIALS_OFFSET;
                initializedPan = true;
            }
        }
        RecipeTreePerfDebug.logPhase("rebuild_structure", startedAt,
                "merge={} nodes={} edges={} rows={} topMaterials={}",
                autoMergeSameMaterials, positionedNodes.size(), edges.size(), mergedLayerRows.size(), topMaterials.size());
    }

    /**
     * 按签名合并未解析输入，尝试套用唯一且当前可编码的 JEI 配方。「已有样板:禁」时仍运行，但若该材料在 ME 已有样板则跳过（shouldBlockExpansion）。
     */
    private boolean processAutoExpandUniqueRecipeSteps(int maxSteps) {
        long startedAt = RecipeTreePerfDebug.begin();
        if (suppressAutoExpandUniqueRecipePass || !autoExpandUniqueEncodableRecipe || !autoExpandUniqueSearchPending) {
            RecipeTreePerfDebug.logPhase("auto_expand_skip", startedAt,
                    "suppressed={} enabled={} pending={}",
                    suppressAutoExpandUniqueRecipePass, autoExpandUniqueEncodableRecipe, autoExpandUniqueSearchPending);
            return false;
        }
        suppressAutoExpandUniqueRecipePass = true;
        try {
            boolean mutated = false;
            int applied = 0;
            for (int step = 0; step < Math.max(1, maxSteps); step++) {
                if (!tryAutoExpandUniqueEncodableRecipeSinglePass()) {
                    break;
                }
                mutated = true;
                applied++;
                autoExpandUniqueCandidateCache.clear();
                rebuildLayoutStructureCore();
            }
            if (mutated) {
                refreshLayoutDerivedCaches();
            }
            autoExpandUniqueSearchPending = mutated;
            RecipeTreePerfDebug.logPhase("auto_expand", startedAt,
                    "steps={} mutated={} nodes={} edges={} rows={}",
                    applied, mutated, positionedNodes.size(), edges.size(), mergedLayerRows.size());
            return mutated;
        } finally {
            suppressAutoExpandUniqueRecipePass = false;
        }
    }

    private boolean tryAutoExpandUniqueEncodableRecipeSinglePass() {
        Map<String, List<UnresolvedInputSlot>> grouped = new LinkedHashMap<>();
        collectUnresolvedInputsGrouped(context.root(), grouped);
        for (List<UnresolvedInputSlot> group : grouped.values()) {
            if (group.isEmpty()) {
                continue;
            }
            if (group.stream().anyMatch(slot -> slot.input().hasAlternativeChoices())) {
                continue;
            }
            MergedLeaf leaf = mergedLeafFromUnresolvedGroup(group);
            if (leaf == null) {
                continue;
            }
            if (shouldBlockExpansion(leaf)) {
                continue;
            }
            ITypedIngredient<?> focus = getJeiSelectionIngredient(leaf.representative());
            if (focus == null || Ae2UtilityJeiPlugin.getJeiRuntime() == null) {
                continue;
            }
            Optional<RecipeTreeRecipeViewModel> chosen = resolveUniqueEncodableRecipe(signatureOf(focus), focus);
            if (chosen.isEmpty()) {
                continue;
            }
            if (wouldCauseRecursiveLeafExpansion(leaf, chosen.get())) {
                continue;
            }
            applyLeafSelection(leaf, chosen.get());
            return true;
        }
        return false;
    }

    private void collectUnresolvedInputsGrouped(RecipeTreeNodeViewModel node, Map<String, List<UnresolvedInputSlot>> grouped) {
        for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
            RecipeTreeNodeViewModel child = input.child();
            if (child == null) {
                grouped.computeIfAbsent(signatureOf(input), k -> new ArrayList<>()).add(new UnresolvedInputSlot(input, node));
            } else {
                collectUnresolvedInputsGrouped(child, grouped);
            }
        }
    }

    private @Nullable MergedLeaf mergedLeafFromUnresolvedGroup(List<UnresolvedInputSlot> group) {
        if (group.isEmpty()) {
            return null;
        }
        RecipeTreeInputViewModel representative = group.getFirst().input();
        RecipeTreeNodeViewModel anchorParent = group.getFirst().parentNode();
        List<RecipeTreeInputViewModel> members = new ArrayList<>(group.size());
        List<RecipeTreeNodeViewModel> parents = new ArrayList<>(group.size());
        int total = 0;
        for (UnresolvedInputSlot slot : group) {
            members.add(slot.input());
            parents.add(slot.parentNode());
            total = safeAdd(total, Math.max(1, slot.input().amount()));
        }
        return new MergedLeaf(representative.displayIngredient(), displayNameOf(representative), total, representative.amountText(),
                anchorParent, List.copyOf(members), List.copyOf(parents));
    }

    private boolean wouldCauseRecursiveLeafExpansion(MergedLeaf leaf, RecipeTreeRecipeViewModel chosen) {
        for (int i = 0; i < leaf.members().size(); i++) {
            if (leaf.parentForMember(i).containsRecipe(chosen)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRecipeSnapshotStrictEncodable(RecipeTreeRecipeViewModel recipe) {
        EncodePatternPacket packet = toEncodePacket(recipe);
        if (packet == null) {
            return false;
        }
        for (List<GenericStack> slot : packet.inputs()) {
            if (slot == null || slot.isEmpty()) {
                return false;
            }
        }
        for (GenericStack out : packet.outputs()) {
            if (out == null) {
                return false;
            }
        }
        return true;
    }

    private Optional<RecipeTreeRecipeViewModel> resolveUniqueEncodableRecipe(String focusSignature, ITypedIngredient<?> focus) {
        if (focusSignature == null || focusSignature.isBlank()) {
            return Optional.empty();
        }
        Optional<RecipeTreeRecipeViewModel> cached = autoExpandUniqueCandidateCache.get(focusSignature);
        if (cached != null) {
            return cached;
        }
        List<RecipeTreeRecipeViewModel> recipes = RecipeTreeJeiLookup.findRecipesByOutput(focus);
        RecipeTreeRecipeViewModel chosen = null;
        for (RecipeTreeRecipeViewModel recipe : recipes) {
            if (!isRecipeSnapshotStrictEncodable(recipe)) {
                continue;
            }
            if (chosen != null) {
                autoExpandUniqueCandidateCache.put(focusSignature, Optional.empty());
                return Optional.empty();
            }
            chosen = recipe;
        }
        Optional<RecipeTreeRecipeViewModel> resolved = Optional.ofNullable(chosen);
        autoExpandUniqueCandidateCache.put(focusSignature, resolved);
        return resolved;
    }

    private record UnresolvedInputSlot(RecipeTreeInputViewModel input, RecipeTreeNodeViewModel parentNode) {
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
        List<RecipeTreeInputViewModel> orderedInputs = new ArrayList<>(node.recipe().inputs());
        List<InputCluster> clusters = autoMergeSameMaterials
                ? mergeSiblingGroupsByLayer(node, orderedInputs)
                : clusterInputsBySignature(orderedInputs);
        clusters = consolidateClustersSharingExpandedChild(clusters);

        for (InputCluster cluster : clusters) {
            List<RecipeTreeInputViewModel> group = cluster.inputs();
            if (group.isEmpty()) {
                continue;
            }

            RecipeTreeInputViewModel representative = group.getFirst();
            int totalRequiredAmount = 0;
            for (RecipeTreeInputViewModel input : group) {
                int amount = safeMultiply(crafts, input.amount());
                totalRequiredAmount = safeAdd(totalRequiredAmount, Math.max(1, amount));
            }

            RecipeTreeNodeViewModel child = representative.child();
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
                addMergedLeafChild(node, children, group, totalRequiredAmount);
            }
        }

        String amountLabel = "";
        String exactAmountLabel = "";
        if (computeRecipeQuantities) {
            amountLabel = crafts > 1 ? "x" + formatCompactCount(crafts) : "";
            exactAmountLabel = crafts > 1
                    ? Component.translatable("gui.ae2utility.recipe_tree.amount_exact", crafts).getString()
                    : "";
        }
        return new GraphNode(node.recipe().primaryOutputIngredient(), node.recipe().title().getString(), amountLabel,
                exactAmountLabel,
                node, node.recipe().subtitleIcon(), node.recipe().subtitle(), null, children,
                computeNodeWidth(amountLabel, node.recipe().subtitleIcon() != null, false));
    }

    private List<LayerRow> buildMergedLayerRows(RecipeTreeNodeViewModel root, int crafts) {
        MergedBuildStats stats = new MergedBuildStats();
        Map<Integer, LayerAccumulator> layers = new LinkedHashMap<>();
        collectMergedLayers(root, crafts, 0, layers, stats);
        List<LayerRow> rows = new ArrayList<>();
        for (LayerAccumulator layer : layers.values()) {
            rows.add(layer.toRow());
        }
        List<LayerRow> built = List.copyOf(rows);
        stats.layerRows = built.size();
        int materialCount = 0;
        for (LayerRow row : built) {
            materialCount += row.materials().size();
        }
        stats.layerMaterials = materialCount;
        lastMergedBuildStats = stats;
        return built;
    }

    private void collectMergedLayers(RecipeTreeNodeViewModel node, int crafts, int depth,
            Map<Integer, LayerAccumulator> layers, MergedBuildStats stats) {
        stats.collectCalls++;
        LayerAccumulator accumulator = layers.computeIfAbsent(depth, ignored -> new LayerAccumulator(depth));
        accumulator.addNode(node, crafts);
        stats.recipeNodeAdds++;
        Map<RecipeTreeNodeViewModel, Integer> aggregatedChildConsumption = new LinkedHashMap<>();
        for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
            RecipeTreeNodeViewModel child = input.child();
            int amount = Math.max(1, safeMultiply(crafts, input.amount()));
            if (child != null) {
                aggregatedChildConsumption.merge(child, amount, RecipeTreeOverviewScreen::safeAdditiveMergeTotals);
                stats.aggregatedChildLinks++;
            } else {
                layers.computeIfAbsent(depth + 1, ignored -> new LayerAccumulator(depth + 1)).addLeaf(input, amount, node);
                stats.leafAdds++;
            }
        }
        for (Map.Entry<RecipeTreeNodeViewModel, Integer> entry : aggregatedChildConsumption.entrySet()) {
            RecipeTreeNodeViewModel child = entry.getKey();
            int childCrafts = ceilDiv(entry.getValue(), child.recipe().primaryOutputCount());
            collectMergedLayers(child, childCrafts, depth + 1, layers, stats);
        }
    }

    /** 合并层递归：同源子配方多次引用时汇总用量后再向下 DFS，避免对同一 child 重复整棵子树。 */
    private static int safeAdditiveMergeTotals(int left, int right) {
        return safeAdd(left, right);
    }

    private String leafAggregateStorageKey(RecipeTreeInputViewModel input) {
        RecipeTreeNodeViewModel child = input.child();
        String base = leafSignatureOf(input);
        if (child == null) {
            return base + "|unexpanded";
        }
        ResourceLocation id = child.recipe().recipeId();
        if (id != null) {
            return base + "|expanded|" + id;
        }
        return base + "|expanded|" + child.recipe().title().getString() + "|"
                + signatureOf(child.recipe().primaryOutputIngredient());
    }

    private String recipeOutputStorageKey(RecipeTreeNodeViewModel node) {
        ITypedIngredient<?> ingredient = node.recipe().primaryOutputIngredient();
        if (ingredient != null) {
            return "R:" + signatureOf(ingredient);
        }
        return "R:" + signatureOfNode(node);
    }

    private List<InputCluster> clusterInputsBySignature(List<RecipeTreeInputViewModel> orderedInputs) {
        Map<String, List<RecipeTreeInputViewModel>> groupedInputs = new LinkedHashMap<>();
        for (RecipeTreeInputViewModel input : orderedInputs) {
            groupedInputs.computeIfAbsent(leafSignatureOf(input), k -> new ArrayList<>()).add(input);
        }
        List<InputCluster> clusters = new ArrayList<>();
        for (Map.Entry<String, List<RecipeTreeInputViewModel>> entry : groupedInputs.entrySet()) {
            clusters.add(new InputCluster(entry.getKey(), 0, new ArrayList<>(entry.getValue())));
        }
        return clusters;
    }

    private List<InputCluster> mergeSiblingGroupsByLayer(RecipeTreeNodeViewModel node,
            List<RecipeTreeInputViewModel> orderedInputs) {
        Map<LayerKey, List<RecipeTreeInputViewModel>> merged = new LinkedHashMap<>();
        for (RecipeTreeInputViewModel input : orderedInputs) {
            String signature = leafSignatureOf(input);
            int layer = layerOf(input.child(), node);
            merged.computeIfAbsent(new LayerKey(signature, layer), ignored -> new ArrayList<>()).add(input);
        }
        List<InputCluster> clusters = new ArrayList<>();
        for (Map.Entry<LayerKey, List<RecipeTreeInputViewModel>> entry : merged.entrySet()) {
            clusters.add(new InputCluster(entry.getKey().signature(), entry.getKey().layer(), new ArrayList<>(entry.getValue())));
        }
        return clusters;
    }

    private int computeMergedLayerContentWidth() {
        int width = 0;
        for (LayerRow row : mergedLayerRows) {
            width = Math.max(width, row.width());
        }
        return Math.max(1, width);
    }

    private int computeMergedLayerContentHeight() {
        if (mergedLayerRows.isEmpty()) {
            return NODE_HEIGHT;
        }
        return mergedLayerRows.size() * LEVEL_GAP;
    }

    private @Nullable LayerMaterialBounds findLayerAt(double logicalMouseX, double logicalMouseY) {
        for (LayerMaterialBounds bounds : layerMaterialBounds) {
            if (bounds.contains(logicalMouseX, logicalMouseY)) {
                return bounds;
            }
        }
        return null;
    }

    private int layerOf(@Nullable RecipeTreeNodeViewModel child, RecipeTreeNodeViewModel parent) {
        if (child == null) {
            return Integer.MAX_VALUE;
        }
        int depth = 0;
        for (RecipeTreeNodeViewModel cursor = child; cursor != null && cursor != parent; cursor = cursor.parent()) {
            depth++;
        }
        return depth;
    }

    private record LayerKey(String signature, int layer) {
    }

    private record InputCluster(String signature, int layer, List<RecipeTreeInputViewModel> inputs) {
    }

    /** 多只输入格子指向同一已展开 {@link RecipeTreeNodeViewModel} 时合成单簇，与合并层上对子树去重递归一致，减少 GraphNode 分支。 */
    private List<InputCluster> consolidateClustersSharingExpandedChild(List<InputCluster> clusters) {
        LinkedHashMap<RecipeTreeNodeViewModel, List<RecipeTreeInputViewModel>> expandedByChild = new LinkedHashMap<>();
        for (InputCluster cluster : clusters) {
            RecipeTreeInputViewModel rep = cluster.inputs().getFirst();
            RecipeTreeNodeViewModel ch = rep.child();
            if (ch != null) {
                expandedByChild.computeIfAbsent(ch, ignored -> new ArrayList<>()).addAll(cluster.inputs());
            }
        }
        if (expandedByChild.isEmpty()) {
            return clusters;
        }
        Set<RecipeTreeNodeViewModel> emittedChild = new HashSet<>();
        List<InputCluster> merged = new ArrayList<>(clusters.size());
        for (InputCluster cluster : clusters) {
            RecipeTreeInputViewModel rep = cluster.inputs().getFirst();
            RecipeTreeNodeViewModel ch = rep.child();
            if (ch != null) {
                if (!emittedChild.add(ch)) {
                    continue;
                }
                List<RecipeTreeInputViewModel> grouped = expandedByChild.get(ch);
                RecipeTreeInputViewModel head = grouped.getFirst();
                merged.add(new InputCluster(leafSignatureOf(head), cluster.layer(), new ArrayList<>(grouped)));
            } else {
                merged.add(cluster);
            }
        }
        return merged;
    }

    private record LayerRow(int depth, List<LayerMaterial> materials) {
        private int width() {
            int total = 0;
            for (int i = 0; i < materials.size(); i++) {
                if (i > 0) {
                    total += 4;
                }
                total += materials.get(i).width();
            }
            return Math.max(24, total + 8);
        }
    }

    private record LayerMaterial(@Nullable ITypedIngredient<?> ingredient, String label, String amountLabel, int width,
            boolean hasAlternatives, int totalAmount, List<RecipeTreeNodeViewModel> recipeTargets,
            List<RecipeTreeInputViewModel> leafInputs, List<RecipeTreeNodeViewModel> leafParents,
            @Nullable IDrawable machineIcon, @Nullable Component machineName, boolean showsPatternHint,
            @Nullable MergedLeaf leafProjection) {
        private LayerMaterial {
            recipeTargets = recipeTargets == null ? List.of() : List.copyOf(recipeTargets);
            leafInputs = leafInputs == null ? List.of() : List.copyOf(leafInputs);
            leafParents = leafParents == null ? List.of() : List.copyOf(leafParents);
        }

        LayerMaterial withShowsPatternHint(boolean hint) {
            return hint == showsPatternHint
                    ? this
                    : new LayerMaterial(ingredient, label, amountLabel, width, hasAlternatives, totalAmount, recipeTargets,
                            leafInputs, leafParents, machineIcon, machineName, hint, leafProjection);
        }

        private boolean hasUnresolvedLeaves() {
            return leafInputs.stream().anyMatch(input -> input.child() == null);
        }
    }

    private record LayerMaterialBounds(LayerRow row, LayerMaterial material, int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private final class LayerAccumulator {
        private final int depth;
        private final Map<String, LayerMaterialAggregate> materials = new LinkedHashMap<>();

        private LayerAccumulator(int depth) {
            this.depth = depth;
        }

        private void addNode(RecipeTreeNodeViewModel node, int crafts) {
            ITypedIngredient<?> ingredient = node.recipe().primaryOutputIngredient();
            String label = displayNameOfOutput(node);
            int amount = safeMultiply(crafts, node.recipe().primaryOutputCount());
            String storageKey = recipeOutputStorageKey(node);
            LayerMaterialAggregate aggregate = materials.computeIfAbsent(storageKey,
                    ignored -> new LayerMaterialAggregate(ingredient, label, false));
            aggregate.addRecipeNode(node, amount, ingredient, label);
        }

        private void addLeaf(RecipeTreeInputViewModel input, int amount, RecipeTreeNodeViewModel parentNode) {
            ITypedIngredient<?> resolvedIngredient = input.displayIngredient();
            if (resolvedIngredient == null) {
                ItemStack stack = input.displayStack();
                if (!stack.isEmpty()) {
                    resolvedIngredient = getIngredientManager() == null
                            ? null
                            : getIngredientManager().createTypedIngredient(stack.copyWithCount(1), true).orElse(null);
                }
            }
            String label = displayNameOf(input);
            String storageKey = "L:" + leafAggregateStorageKey(input);
            final ITypedIngredient<?> ingredientForAggregate = resolvedIngredient;
            LayerMaterialAggregate aggregate = materials.computeIfAbsent(storageKey,
                    ignored -> new LayerMaterialAggregate(ingredientForAggregate, label, input.hasAlternativeChoices()));
            aggregate.addLeaf(input, amount, parentNode, resolvedIngredient, label, input.hasAlternativeChoices());
        }

        private LayerRow toRow() {
            List<LayerMaterial> rowMaterials = new ArrayList<>();
            for (LayerMaterialAggregate aggregate : materials.values()) {
                rowMaterials.add(aggregate.toLayerMaterial());
            }
            return new LayerRow(depth, List.copyOf(rowMaterials));
        }
    }

    private final class LayerMaterialAggregate {
        private @Nullable ITypedIngredient<?> ingredient;
        private String label;
        private boolean hasAlternatives;
        private final List<RecipeTreeNodeViewModel> recipeTargets = new ArrayList<>();
        private final List<RecipeTreeInputViewModel> leafInputs = new ArrayList<>();
        private final List<RecipeTreeNodeViewModel> leafParents = new ArrayList<>();
        private int amount;
        private @Nullable IDrawable machineIcon;
        private @Nullable Component machineName;

        private LayerMaterialAggregate(@Nullable ITypedIngredient<?> ingredient, String label, boolean initialAlternatives) {
            this.ingredient = ingredient;
            this.label = label == null ? "" : label;
            this.hasAlternatives = initialAlternatives;
            this.amount = 0;
            this.machineIcon = null;
            this.machineName = null;
        }

        void addRecipeNode(RecipeTreeNodeViewModel node, int contribution, @Nullable ITypedIngredient<?> ing, String nodeLabel) {
            recipeTargets.add(node);
            amount = safeAdd(amount, Math.max(1, contribution));
            if (ingredient == null && ing != null) {
                ingredient = ing;
            }
            if (nodeLabel != null && !nodeLabel.isBlank()) {
                label = nodeLabel;
            }
            IDrawable icon = node.recipe().subtitleIcon();
            if (machineIcon == null && icon != null) {
                machineIcon = icon;
            }
            if (machineName == null && node.recipe().subtitle() != null) {
                machineName = node.recipe().subtitle();
            }
        }

        void addLeaf(RecipeTreeInputViewModel input, int contribution, RecipeTreeNodeViewModel parentNode,
                @Nullable ITypedIngredient<?> ing, String leafLabel, boolean alternatives) {
            leafInputs.add(input);
            leafParents.add(parentNode);
            amount = safeAdd(amount, Math.max(1, contribution));
            hasAlternatives |= alternatives;
            if (ingredient == null && ing != null) {
                ingredient = ing;
            }
            if (leafLabel != null && !leafLabel.isBlank()) {
                label = leafLabel;
            }
        }

        LayerMaterial toLayerMaterial() {
            int displayAmount = Math.max(1, amount);
            String amountLabel = computeRecipeQuantities
                    ? formatLayerMaterialAmountLabel(ingredient, List.copyOf(leafInputs), displayAmount)
                    : "";
            boolean showAlternativesButton = hasAlternatives && leafInputs.stream().anyMatch(input -> input.child() == null);
            int slotWidth = computeNodeWidth(amountLabel, machineIcon != null, showAlternativesButton);
            boolean hint = RecipeTreeOverviewScreen.this.computeLayerMaterialShowsPatternHint(ingredient, label, amount,
                    List.copyOf(recipeTargets), List.copyOf(leafInputs), List.copyOf(leafParents));
            MergedLeaf projection = null;
            if (!leafInputs.isEmpty()) {
                RecipeTreeInputViewModel rep = leafInputs.getFirst();
                RecipeTreeNodeViewModel primaryParent = leafParents.getFirst();
                projection = new MergedLeaf(ingredient, label, Math.max(1, amount), rep.amountText(), primaryParent,
                        List.copyOf(leafInputs), List.copyOf(leafParents));
            }
            return new LayerMaterial(ingredient, label, amountLabel, slotWidth, showAlternativesButton, amount,
                    List.copyOf(recipeTargets), List.copyOf(leafInputs), List.copyOf(leafParents), machineIcon, machineName, hint,
                    projection);
        }
    }




    private @Nullable MergedLeaf mergedLeafFromLayerMaterial(LayerMaterial material) {
        return material.leafProjection();
    }

    private boolean computeLayerMaterialShowsPatternHint(LayerMaterial material) {
        return computeLayerMaterialShowsPatternHint(material.ingredient(), material.label(), material.totalAmount(),
                material.recipeTargets(), material.leafInputs(), material.leafParents());
    }

    private boolean computeLayerMaterialShowsPatternHint(@Nullable ITypedIngredient<?> ingredient, String label, int totalAmount,
            List<RecipeTreeNodeViewModel> recipeTargets, List<RecipeTreeInputViewModel> leafInputs,
            List<RecipeTreeNodeViewModel> leafParents) {
        if (!shouldShowMeExistingPatternHints()) {
            return false;
        }
        if (!leafInputs.isEmpty() && !leafParents.isEmpty()) {
            RecipeTreeInputViewModel representative = leafInputs.getFirst();
            RecipeTreeNodeViewModel primaryParent = leafParents.getFirst();
            MergedLeaf leaf = new MergedLeaf(ingredient, label, totalAmount, representative.amountText(), primaryParent,
                    List.copyOf(leafInputs), List.copyOf(leafParents));
            if (hasExistingPatternForLeaf(leaf)) {
                return true;
            }
        }
        for (RecipeTreeNodeViewModel node : recipeTargets) {
            if (hasExistingPatternForOutput(node.recipe())) {
                return true;
            }
        }
        return false;
    }

    /** 仅在「已有样板:禁」且开启数量演算时，才做 ME 已有样板检测与红框/tooltip */
    private boolean shouldShowMeExistingPatternHints() {
        return context.disableExistingPatternExpansion() && computeRecipeQuantities;
    }

    private boolean shouldShowTopMaterialPatternHint(RequestedIngredient mat) {
        return shouldShowMeExistingPatternHints() && hasExistingPatternForRequestedIngredient(mat);
    }

    private void addMergedLeafChild(RecipeTreeNodeViewModel node, List<GraphNode> children,
            List<RecipeTreeInputViewModel> group, int totalRequiredAmount) {
        if (group.isEmpty()) {
            return;
        }
        RecipeTreeInputViewModel representative = group.get(0);
        MergedLeaf leaf = new MergedLeaf(representative.displayIngredient(), displayNameOf(representative), totalRequiredAmount,
                representative.amountText(), node, List.copyOf(group), List.of());
        String amountLabel = computeRecipeQuantities ? formatMergedLeafAmount(leaf) : "";
        String exact = computeRecipeQuantities ? exactAmountOf(leaf) : "";
        children.add(new GraphNode(leaf.ingredient(), leaf.title(), amountLabel, exact, null, null, null, leaf,
                List.of(), computeNodeWidth(amountLabel, false, leaf.representative().hasAlternativeChoices())));
    }

    private static int computeNodeWidth(String amountLabel, boolean hasMachineIcon, boolean hasAlternativeButton) {
        int width = 24 + (amountLabel == null || amountLabel.isBlank() ? 0 : Minecraft.getInstance().font.width(amountLabel) + 6);
        if (hasMachineIcon) {
            width += MACHINE_SLOT_SIZE + 4;
        }
        if (hasAlternativeButton) {
            width += 14;
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
        return Component.translatable("gui.ae2utility.recipe_tree.amount_exact", Math.max(1, leaf.totalAmount())).getString();
    }

    private static String formatLayerMaterialAmountLabel(@Nullable ITypedIngredient<?> ingredient,
            List<RecipeTreeInputViewModel> leafInputs, int totalAmount) {
        int safe = Math.max(1, totalAmount);
        if (!leafInputs.isEmpty()) {
            String sampleText = leafInputs.getFirst().amountText();
            if (sampleText != null && sampleText.contains("mB")) {
                return safe + " mB";
            }
            if (sampleText != null && !sampleText.isBlank() && !sampleText.startsWith("x")) {
                return sampleText;
            }
        }
        if (ingredient != null) {
            if (ingredient.getIngredient(NeoForgeTypes.FLUID_STACK).filter(fs -> !fs.isEmpty()).isPresent()) {
                return safe + " mB";
            }
            Object raw = ingredient.getIngredient();
            if (GenericIngredientUtil.tryGetMekanismChemicalAmount(raw) > 0L) {
                return safe + " mB";
            }
        }
        return "x" + formatCompactCount(safe);
    }

    private String leafSignatureOf(RecipeTreeInputViewModel input) {
        var requested = input.requestedIngredientView();
        if (requested != null && !requested.alternatives().isEmpty()) {
            String signature = input.requestedIngredientSignature();
            if (signature != null && !signature.isBlank()) {
                return signature;
            }
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
        long startedAt = RecipeTreePerfDebug.begin();
        lastRenderedNodeCount = 0;
        lastRenderedEdgeCount = 0;
        lastRenderedLayerCount = 0;
        lastRenderedLayerMaterialCount = 0;
        lastRenderedTopMaterialCount = 0;
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
        if (autoMergeSameMaterials) {
            renderMergedLayerEdges(graphics);
            renderMergedLayers(graphics);
            graphics.pose().translate(0.0F, 0.0F, 1.0F);
            renderTopMaterialsMerged(graphics);
        } else {
            renderEdges(graphics);
            graphics.pose().translate(0.0F, 0.0F, 1.0F);
            renderTopMaterials(graphics);
            renderNodes(graphics);
        }
        graphics.pose().popPose();

        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(this.font, this.title, 10, 12, 0xFFFFFFFF, false);
        graphics.drawString(this.font, Component.translatable("gui.ae2utility.recipe_tree.overview_hint"), 10, 26, 0xFFDDE6EE, false);
        graphics.drawString(this.font, cachedRequiredPatternsTitleLine, 10, 40, 0xFFEAF4FF, false);

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 320.0F);
        renderPatternSubstitutionToggles(graphics);
        graphics.pose().popPose();

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 300.0F);
        renderAlternativeSelection(graphics, mouseX, mouseY);
        graphics.pose().popPose();

        double logicalMouseX = (mouseX - panX) / zoom;
        double logicalMouseY = (mouseY - panY) / zoom;
        renderTooltip(graphics, logicalMouseX, logicalMouseY, mouseX, mouseY);
        RecipeTreePerfDebug.logRenderSummary(startedAt,
                "merge={} qty={} totalNodes={} visibleNodes={} totalEdges={} visibleEdges={} rows={} visibleRows={} visibleLayerMaterials={} topMaterialsVisible={} zoom={}",
                autoMergeSameMaterials, computeRecipeQuantities,
                positionedNodes.size(), lastRenderedNodeCount,
                edges.size(), lastRenderedEdgeCount,
                mergedLayerRows.size(), lastRenderedLayerCount, lastRenderedLayerMaterialCount,
                lastRenderedTopMaterialCount,
                String.format(java.util.Locale.ROOT, "%.2f", zoom));
    }

    private void renderEdges(GuiGraphics graphics) {
        for (Edge edge : edges) {
            int startX = edge.parent().x() + edge.parent().graph().width() / 2;
            int startY = edge.parent().y() + NODE_HEIGHT;
            int endX = edge.child().x() + edge.child().graph().width() / 2;
            int endY = edge.child().y();
            if (!isLogicalRectVisible(Math.min(startX, endX) - 2, startY, Math.max(startX, endX) + 2, endY)) {
                continue;
            }
            lastRenderedEdgeCount++;
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
        alternativeButtonBounds.clear();
        for (PositionedNode node : positionedNodes) {
            int x = node.x();
            int y = node.y();
            int width = node.graph().width();
            if (!isLogicalRectVisible(x, y, x + width, y + NODE_HEIGHT)) {
                continue;
            }
            lastRenderedNodeCount++;
            int border = node.graph().children().isEmpty() ? 0xFFC3CBD3 : 0xFFFFFFFF;
            graphics.fill(x, y, x + width, y + NODE_HEIGHT, 0xCC11161B);
            graphics.fill(x, y, x + width, y + 1, border);
            graphics.fill(x, y + NODE_HEIGHT - 1, x + width, y + NODE_HEIGHT, border);
            graphics.fill(x, y, x + 1, y + NODE_HEIGHT, border);
            graphics.fill(x + width - 1, y, x + width, y + NODE_HEIGHT, border);

            renderIngredientAt(graphics, node.graph().ingredient(), x + 4, y + 5);
            if (!node.graph().amount().isBlank()) {
                graphics.drawString(this.font, node.graph().amount(), x + 24, y + 9, 0xFFD4DEE7, false);
            }
            if (node.graph().machineIcon() != null) {
                renderMachineSlot(graphics, node.graph().machineIcon(), x + width - MACHINE_SLOT_SIZE - 3, y + 4);
            }
            if (node.graph().mergedLeaf() != null && node.graph().mergedLeaf().representative().hasAlternativeChoices()) {
                int buttonX = x + width - 13;
                int buttonY = y + 8;
                renderAlternativeButton(graphics, buttonX, buttonY);
                alternativeButtonBounds.add(AlternativeButtonBounds.forLeaf(node.graph().mergedLeaf(), buttonX, buttonY, 10, 10));
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

    private void renderMergedLayerEdges(GuiGraphics graphics) {
        if (mergedLayerRows.size() < 2) {
            return;
        }
        int contentWidth = computeMergedLayerContentWidth();
        int startX = 36;
        int baseY = 42 + TOP_MATERIALS_OFFSET;
        for (int idx = 0; idx < mergedLayerRows.size() - 1; idx++) {
            LayerRow rowA = mergedLayerRows.get(idx);
            LayerRow rowB = mergedLayerRows.get(idx + 1);
            int rowAX = startX + Math.max(0, (contentWidth - rowA.width()) / 2);
            int rowBX = startX + Math.max(0, (contentWidth - rowB.width()) / 2);
            int rowAY = baseY + idx * LEVEL_GAP;
            int rowBY = baseY + (idx + 1) * LEVEL_GAP;
            int startXc = rowAX + rowA.width() / 2;
            int startY = rowAY + NODE_HEIGHT;
            int endXc = rowBX + rowB.width() / 2;
            int endY = rowBY;
            if (!isLogicalRectVisible(Math.min(startXc, endXc) - 2, startY, Math.max(startXc, endXc) + 2, endY)) {
                continue;
            }
            lastRenderedEdgeCount++;
            int midY = startY + Math.max(8, (endY - startY) / 2);
            graphics.fill(startXc - 1, startY, startXc + 2, midY, 0xB0000000);
            graphics.fill(Math.min(startXc, endXc), midY - 1, Math.max(startXc, endXc) + 1, midY + 2, 0xB0000000);
            graphics.fill(endXc - 1, midY, endXc + 2, endY, 0xB0000000);
            graphics.fill(startXc, startY, startXc + 1, midY, 0xFFFFFFFF);
            graphics.fill(Math.min(startXc, endXc), midY, Math.max(startXc, endXc) + 1, midY + 1, 0xFFFFFFFF);
            graphics.fill(endXc, midY, endXc + 1, endY, 0xFFFFFFFF);
        }
    }

    private void renderMergedLayers(GuiGraphics graphics) {
        layerMaterialBounds.clear();
        alternativeButtonBounds.clear();
        if (mergedLayerRows.isEmpty()) {
            return;
        }
        int contentWidth = computeMergedLayerContentWidth();
        int startX = 36;
        int y = 42 + TOP_MATERIALS_OFFSET;
        for (int i = 0; i < mergedLayerRows.size(); i++) {
            LayerRow row = mergedLayerRows.get(i);
            int rowX = startX + Math.max(0, (contentWidth - row.width()) / 2);
            int rowY = y + i * LEVEL_GAP;
            if (!isLogicalRectVisible(rowX, rowY, rowX + row.width(), rowY + NODE_HEIGHT)) {
                continue;
            }
            lastRenderedLayerCount++;
            graphics.fill(rowX, rowY, rowX + row.width(), rowY + NODE_HEIGHT, 0xCC11161B);
            graphics.fill(rowX, rowY, rowX + row.width(), rowY + 1, 0xFFFFFFFF);
            graphics.fill(rowX, rowY + NODE_HEIGHT - 1, rowX + row.width(), rowY + NODE_HEIGHT, 0xFFFFFFFF);
            graphics.fill(rowX, rowY, rowX + 1, rowY + NODE_HEIGHT, 0xFFFFFFFF);
            graphics.fill(rowX + row.width() - 1, rowY, rowX + row.width(), rowY + NODE_HEIGHT, 0xFFFFFFFF);

            int currentX = rowX + 4;
            for (LayerMaterial material : row.materials()) {
                int materialWidth = material.width();
                if (!isLogicalRectVisible(currentX, rowY, currentX + materialWidth, rowY + NODE_HEIGHT)) {
                    currentX += materialWidth + 4;
                    continue;
                }
                lastRenderedLayerMaterialCount++;
                int innerBorder = material.showsPatternHint() ? 0xFFE07A7A : 0xFFFFFFFF;
                graphics.fill(currentX, rowY + 3, currentX + materialWidth, rowY + NODE_HEIGHT - 3, 0xFF273038);
                graphics.fill(currentX, rowY + 3, currentX + materialWidth, rowY + 4, innerBorder);
                graphics.fill(currentX, rowY + NODE_HEIGHT - 4, currentX + materialWidth, rowY + NODE_HEIGHT - 3, innerBorder);
                graphics.fill(currentX, rowY + 3, currentX + 1, rowY + NODE_HEIGHT - 3, innerBorder);
                graphics.fill(currentX + materialWidth - 1, rowY + 3, currentX + materialWidth, rowY + NODE_HEIGHT - 3, innerBorder);
                renderIngredientAt(graphics, material.ingredient(), currentX + 2, rowY + 5);
                if (!material.amountLabel().isBlank()) {
                    graphics.drawString(this.font, material.amountLabel(), currentX + 22, rowY + 9,
                            0xFFD4DEE7, false);
                }
                if (material.machineIcon() != null) {
                    renderMachineSlot(graphics, material.machineIcon(), currentX + materialWidth - MACHINE_SLOT_SIZE - 3, rowY + 4);
                }
                if (material.hasAlternatives() && material.hasUnresolvedLeaves()) {
                    MergedLeaf altLeaf = material.leafProjection();
                    if (altLeaf != null) {
                        int buttonX = currentX + materialWidth - 13;
                        int buttonY = rowY + 8;
                        renderAlternativeButton(graphics, buttonX, buttonY);
                        alternativeButtonBounds.add(AlternativeButtonBounds.forLeaf(altLeaf, buttonX, buttonY, 10, 10));
                    }
                }
                layerMaterialBounds.add(new LayerMaterialBounds(row, material, currentX, rowY, materialWidth, NODE_HEIGHT));
                currentX += materialWidth + 4;
            }
        }
    }

    private void renderTopMaterialsMerged(GuiGraphics graphics) {
        topMaterialBounds.clear();
        if (topMaterials == null || topMaterials.isEmpty() || mergedLayerRows.isEmpty()) {
            return;
        }
        LayerRow rootRow = mergedLayerRows.getFirst();
        int contentWidth = computeMergedLayerContentWidth();
        int startX = 36;
        int rowX = startX + Math.max(0, (contentWidth - rootRow.width()) / 2);
        int rootY = 42 + TOP_MATERIALS_OFFSET;
        int rootCenterX = rowX + rootRow.width() / 2;

        int gap = 4;
        int totalWidth = -gap;
        int[] widths = new int[topMaterials.size()];
        String[] labels = new String[topMaterials.size()];

        for (int i = 0; i < topMaterials.size(); i++) {
            RequestedIngredient mat = topMaterials.get(i);
            String label = computeRecipeQuantities ? "x" + formatCompactCount(mat.count()) : "";
            labels[i] = label;
            int w = 24 + (label.isEmpty() ? 0 : this.font.width(label) + 6);
            if (mat.alternatives().size() > 1) {
                w += 14;
            }
            widths[i] = w;
            totalWidth += w + gap;
        }

        int materialsStartX = rootCenterX - totalWidth / 2;
        int panelY = rootY - NODE_HEIGHT - 24;
        if (!isLogicalRectVisible(materialsStartX - 4, panelY - 4, materialsStartX + totalWidth + 4, panelY + NODE_HEIGHT + 4)) {
            return;
        }
        fillFramedPanel(graphics, materialsStartX - 4, panelY - 4, materialsStartX + totalWidth + 4, panelY + NODE_HEIGHT + 4,
                0xFFFFFFFF, 0xFF4D5962, 0xCC11161B);

        int currentX = materialsStartX;
        for (int i = 0; i < topMaterials.size(); i++) {
            RequestedIngredient mat = topMaterials.get(i);
            int width = widths[i];
            int slotBorder = shouldShowTopMaterialPatternHint(mat) ? 0xFFE07A7A : 0xFFFFFFFF;

            graphics.fill(currentX, panelY, currentX + width, panelY + NODE_HEIGHT, 0xCC11161B);
            graphics.fill(currentX, panelY, currentX + width, panelY + 1, slotBorder);
            graphics.fill(currentX, panelY + NODE_HEIGHT - 1, currentX + width, panelY + NODE_HEIGHT, slotBorder);
            graphics.fill(currentX, panelY, currentX + 1, panelY + NODE_HEIGHT, slotBorder);
            graphics.fill(currentX + width - 1, panelY, currentX + width, panelY + NODE_HEIGHT, slotBorder);

            ItemStack itemStack = ItemStack.EMPTY;
            if (!mat.alternatives().isEmpty()) {
                int stackCount = computeRecipeQuantities ? mat.count() : 1;
                itemStack = mat.alternatives().getFirst().copyWithCount(stackCount);
            }
            if (!itemStack.isEmpty()) {
                lastRenderedTopMaterialCount++;
                graphics.renderItem(itemStack, currentX + 4, panelY + 5);
                if (!labels[i].isEmpty()) {
                    graphics.drawString(this.font, labels[i], currentX + 24, panelY + 9, 0xFFD4DEE7, false);
                }
                topMaterialBounds.add(new TopMaterialBounds(mat, currentX, panelY, width, NODE_HEIGHT));
            }
            if (mat.alternatives().size() > 1) {
                int buttonX = currentX + width - 13;
                int buttonY = panelY + 8;
                renderAlternativeButton(graphics, buttonX, buttonY);
                alternativeButtonBounds.add(AlternativeButtonBounds.forMaterial(mat, buttonX, buttonY, 10, 10));
            }

            currentX += width + gap;
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
            String label = computeRecipeQuantities ? "x" + formatCompactCount(mat.count()) : "";
            labels[i] = label;
            int w = 24 + (label.isEmpty() ? 0 : this.font.width(label) + 6);
            if (mat.alternatives().size() > 1) {
                w += 14;
            }
            widths[i] = w;
            totalWidth += w + gap;
        }

        int startX = rootCenterX - totalWidth / 2;
        int y = rootY - NODE_HEIGHT - 24;
        if (!isLogicalRectVisible(startX - 4, y - 4, startX + totalWidth + 4, y + NODE_HEIGHT + 4)) {
            return;
        }
        fillFramedPanel(graphics, startX - 4, y - 4, startX + totalWidth + 4, y + NODE_HEIGHT + 4,
                0xFFFFFFFF, 0xFF4D5962, 0xCC11161B);

        int currentX = startX;
        for (int i = 0; i < topMaterials.size(); i++) {
            RequestedIngredient mat = topMaterials.get(i);
            int width = widths[i];
            int slotBorder = shouldShowTopMaterialPatternHint(mat) ? 0xFFE07A7A : 0xFFFFFFFF;

            graphics.fill(currentX, y, currentX + width, y + NODE_HEIGHT, 0xCC11161B);
            graphics.fill(currentX, y, currentX + width, y + 1, slotBorder);
            graphics.fill(currentX, y + NODE_HEIGHT - 1, currentX + width, y + NODE_HEIGHT, slotBorder);
            graphics.fill(currentX, y, currentX + 1, y + NODE_HEIGHT, slotBorder);
            graphics.fill(currentX + width - 1, y, currentX + width, y + NODE_HEIGHT, slotBorder);

            ItemStack itemStack = ItemStack.EMPTY;
            if (!mat.alternatives().isEmpty()) {
                int stackCount = computeRecipeQuantities ? mat.count() : 1;
                itemStack = mat.alternatives().get(0).copyWithCount(stackCount);
            }
            if (!itemStack.isEmpty()) {
                lastRenderedTopMaterialCount++;
                graphics.renderItem(itemStack, currentX + 4, y + 5);
                if (!labels[i].isEmpty()) {
                    graphics.drawString(this.font, labels[i], currentX + 24, y + 9, 0xFFD4DEE7, false);
                }
                topMaterialBounds.add(new TopMaterialBounds(mat, currentX, y, width, NODE_HEIGHT));
            }
            if (mat.alternatives().size() > 1) {
                int buttonX = currentX + width - 13;
                int buttonY = y + 8;
                renderAlternativeButton(graphics, buttonX, buttonY);
                alternativeButtonBounds.add(AlternativeButtonBounds.forMaterial(mat, buttonX, buttonY, 10, 10));
            }

            currentX += width + gap;
        }
    }

    private void renderAlternativeButton(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 10, y + 10, 0xFF4D5962);
        graphics.fill(x + 1, y + 1, x + 9, y + 9, 0xFF30373D);
        graphics.drawString(this.font, "v", x + 2, y + 1, 0xFFFFFFFF, false);
    }

    private void renderMachineSlot(GuiGraphics graphics, IDrawable icon, int x, int y) {
        graphics.fill(x, y, x + MACHINE_SLOT_SIZE, y + MACHINE_SLOT_SIZE, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + MACHINE_SLOT_SIZE - 1, y + MACHINE_SLOT_SIZE - 1, 0xFFBFC9D2);
        graphics.fill(x + 2, y + 2, x + MACHINE_SLOT_SIZE - 2, y + MACHINE_SLOT_SIZE - 2, 0xFF2A3137);
        icon.draw(graphics, x + 1, y + 1);
    }

    private int substitutionToggleItemX() {
        return 10;
    }

    private int substitutionToggleY() {
        return this.height - 25;
    }

    private int substitutionToggleFluidX() {
        return substitutionToggleItemX() + SUBSTITUTION_ICON_DST + 6;
    }

    private double visibleLogicalMinX() {
        return (-panX) / zoom - VISIBILITY_MARGIN;
    }

    private double visibleLogicalMaxX() {
        return (this.width - panX) / zoom + VISIBILITY_MARGIN;
    }

    private double visibleLogicalMinY() {
        return (-panY) / zoom - VISIBILITY_MARGIN;
    }

    private double visibleLogicalMaxY() {
        return (this.height - panY) / zoom + VISIBILITY_MARGIN;
    }

    private boolean isLogicalRectVisible(double left, double top, double right, double bottom) {
        return right >= visibleLogicalMinX()
                && left <= visibleLogicalMaxX()
                && bottom >= visibleLogicalMinY()
                && top <= visibleLogicalMaxY();
    }

    private void renderPatternSubstitutionToggles(GuiGraphics graphics) {
        float scale = SUBSTITUTION_ICON_DST / (float) SUBSTITUTION_ICON_SRC;
        int iy = substitutionToggleY();
        int ix = substitutionToggleItemX();
        int fx = substitutionToggleFluidX();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();

        graphics.pose().pushPose();
        graphics.pose().translate(ix, iy, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        Icon iconItem = JeiPatternSubstitutionUi.isItemSubstituteOn()
                ? Icon.S_SUBSTITUTION_ENABLED
                : Icon.S_SUBSTITUTION_DISABLED;
        iconItem.getBlitter().dest(0, 0, SUBSTITUTION_ICON_SRC, SUBSTITUTION_ICON_SRC).blit(graphics);
        graphics.pose().popPose();

        graphics.pose().pushPose();
        graphics.pose().translate(fx, iy, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        Icon iconFluid = JeiPatternSubstitutionUi.isFluidSubstituteOn()
                ? Icon.S_FLUID_SUBSTITUTION_ENABLED
                : Icon.S_FLUID_SUBSTITUTION_DISABLED;
        iconFluid.getBlitter().dest(0, 0, SUBSTITUTION_ICON_SRC, SUBSTITUTION_ICON_SRC).blit(graphics);
        graphics.pose().popPose();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private boolean handlePatternSubstitutionToggleClick(double mouseX, double mouseY) {
        int ix = substitutionToggleItemX();
        int iy = substitutionToggleY();
        int fx = substitutionToggleFluidX();
        int s = SUBSTITUTION_ICON_DST;
        if (mouseX >= ix && mouseX < ix + s && mouseY >= iy && mouseY < iy + s) {
            JeiPatternSubstitutionUi.toggleItemSubstitute();
            return true;
        }
        if (mouseX >= fx && mouseX < fx + s && mouseY >= iy && mouseY < iy + s) {
            JeiPatternSubstitutionUi.toggleFluidSubstitute();
            return true;
        }
        return false;
    }

    private @Nullable List<Component> patternSubstitutionTooltipAt(int mouseX, int mouseY) {
        int ix = substitutionToggleItemX();
        int iy = substitutionToggleY();
        int fx = substitutionToggleFluidX();
        int s = SUBSTITUTION_ICON_DST;
        if (mouseX >= ix && mouseX < ix + s && mouseY >= iy && mouseY < iy + s) {
            return buildAeItemSubstitutionTooltipLines();
        }
        if (mouseX >= fx && mouseX < fx + s && mouseY >= iy && mouseY < iy + s) {
            return buildAeFluidSubstitutionTooltipLines();
        }
        return null;
    }

    private List<Component> buildAeItemSubstitutionTooltipLines() {
        List<Component> lines = new ArrayList<>(3);
        if (JeiPatternSubstitutionUi.isItemSubstituteOn()) {
            lines.add(Component.translatable("gui.tooltips.ae2.SubstitutionsOn"));
            lines.add(Component.translatable("gui.tooltips.ae2.SubstitutionsDescEnabled").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("gui.tooltips.ae2.SubstitutionsOff"));
            lines.add(Component.translatable("gui.tooltips.ae2.SubstitutionsDescDisabled").withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("gui.ae2utility.recipe_tree.overview_substitution_encode_hint").withStyle(ChatFormatting.DARK_AQUA));
        return lines;
    }

    private List<Component> buildAeFluidSubstitutionTooltipLines() {
        List<Component> lines = new ArrayList<>(3);
        lines.add(Component.translatable("gui.tooltips.ae2.FluidSubstitutions"));
        if (JeiPatternSubstitutionUi.isFluidSubstituteOn()) {
            lines.add(Component.translatable("gui.tooltips.ae2.FluidSubstitutionsDescEnabled").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("gui.tooltips.ae2.FluidSubstitutionsDescDisabled").withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("gui.ae2utility.recipe_tree.overview_substitution_encode_hint").withStyle(ChatFormatting.DARK_AQUA));
        return lines;
    }

    private void renderTooltip(GuiGraphics graphics, double logicalMouseX, double logicalMouseY, int mouseX, int mouseY) {
        if (pendingAlternativeSelection != null) {
            for (AlternativeOptionBounds option : alternativeOptionBounds) {
                if (option.contains(mouseX, mouseY) && option.ingredient() != null) {
                    renderVanillaIngredientTooltip(graphics, option.ingredient(), mouseX, mouseY);
                    return;
                }
            }
        }
        List<Component> substitutionTooltip = patternSubstitutionTooltipAt(mouseX, mouseY);
        if (substitutionTooltip != null) {
            graphics.renderTooltip(this.font, substitutionTooltip, Optional.empty(), mouseX, mouseY);
            return;
        }
        if (autoMergeSameMaterials) {
            if (encodeButton.visible && isPointInsideButton(encodeButton, mouseX, mouseY)) {
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable("gui.ae2utility.recipe_tree.overview_encode_tooltip")),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
            if (uploadButton.visible && isPointInsideButton(uploadButton, mouseX, mouseY)) {
                graphics.renderTooltip(this.font,
                        List.of(uploadButton.active
                                ? Component.translatable("gui.ae2utility.recipe_tree.overview_upload_tooltip")
                                : Component.translatable("gui.ae2utility.recipe_tree.upload_missing_eaep")),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
            if (autoMergeButton.visible && isPointInsideButton(autoMergeButton, mouseX, mouseY)) {
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable("gui.ae2utility.recipe_tree.overview_merge_tooltip")),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
            if (autoUniqueRecipeButton.visible && isPointInsideButton(autoUniqueRecipeButton, mouseX, mouseY)) {
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable("gui.ae2utility.recipe_tree.overview_auto_unique_tooltip")),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
            if (computeQuantitiesButton.visible && isPointInsideButton(computeQuantitiesButton, mouseX, mouseY)) {
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable("gui.ae2utility.recipe_tree.overview_quantity_compute_tooltip")),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
            for (AlternativeButtonBounds bounds : alternativeButtonBounds) {
                if (bounds.contains(logicalMouseX, logicalMouseY)) {
                    graphics.renderTooltip(this.font,
                            List.of(Component.translatable("gui.ae2utility.recipe_tree.alternative_button_hint")),
                            Optional.empty(), mouseX, mouseY);
                    return;
                }
            }
            for (LayerMaterialBounds bounds : layerMaterialBounds) {
                if (bounds.contains(logicalMouseX, logicalMouseY)) {
                    List<Component> lines = new ArrayList<>();
                    lines.add(Component.literal(bounds.material().label()));
                    if (!bounds.material().amountLabel().isBlank()) {
                        lines.add(Component.literal(bounds.material().amountLabel()).withStyle(ChatFormatting.GRAY));
                    }
                    MergedLeaf leafPreview = mergedLeafFromLayerMaterial(bounds.material());
                    if (bounds.material().showsPatternHint()) {
                        lines.add(Component.translatable("gui.ae2utility.recipe_tree.pattern_exists").withStyle(ChatFormatting.RED));
                    }
                    if (!bounds.material().recipeTargets().isEmpty()) {
                        RecipeTreeNodeViewModel node = bounds.material().recipeTargets().getFirst();
                        if (!hasJeiRecipes(node)) {
                            lines.add(Component.translatable("gui.ae2utility.recipe_tree.no_jei_recipe").withStyle(ChatFormatting.AQUA));
                        }
                        if (node.recipe().subtitle() != null && !node.recipe().subtitle().getString().isBlank()) {
                            lines.add(node.recipe().subtitle().copy().withStyle(ChatFormatting.GRAY));
                        }
                    } else if (leafPreview != null && !hasJeiRecipes(leafPreview)) {
                        lines.add(Component.translatable("gui.ae2utility.recipe_tree.no_jei_recipe").withStyle(ChatFormatting.AQUA));
                    }
                    graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
                    return;
                }
            }
            for (TopMaterialBounds bounds : topMaterialBounds) {
                if (!bounds.contains(logicalMouseX, logicalMouseY) || bounds.material().alternatives().isEmpty()) {
                    continue;
                }
                ItemStack stack = bounds.material().alternatives().getFirst();
                List<Component> lines = new ArrayList<>();
                lines.add(stack.getHoverName());
                if (computeRecipeQuantities) {
                    lines.add(Component.translatable("gui.ae2utility.recipe_tree.amount_exact", bounds.material().count())
                            .withStyle(ChatFormatting.GRAY));
                }
                lines.add(Component.translatable("gui.ae2utility.recipe_tree.material_right_click_hint")
                        .withStyle(ChatFormatting.GRAY));
                if (shouldShowTopMaterialPatternHint(bounds.material())) {
                    lines.add(Component.translatable("gui.ae2utility.recipe_tree.me_pattern_exists_hint").withStyle(ChatFormatting.RED));
                }
                graphics.renderTooltip(this.font, lines, Optional.empty(), mouseX, mouseY);
                return;
            }
            if (toggleExistingPatternButton.visible && isPointInsideButton(toggleExistingPatternButton, mouseX, mouseY)) {
                graphics.renderTooltip(this.font,
                        List.of(Component.literal(context.disableExistingPatternExpansion()
                                ? Component.translatable("gui.ae2utility.recipe_tree.overview_toggle_existing_disabled").getString()
                                : Component.translatable("gui.ae2utility.recipe_tree.overview_toggle_existing_enabled").getString())),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
            return;
        }
        if (encodeButton.visible && isPointInsideButton(encodeButton, mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    List.of(Component.translatable("gui.ae2utility.recipe_tree.overview_encode_tooltip")),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        if (uploadButton.visible && isPointInsideButton(uploadButton, mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    List.of(uploadButton.active
                            ? Component.translatable("gui.ae2utility.recipe_tree.overview_upload_tooltip")
                            : Component.translatable("gui.ae2utility.recipe_tree.upload_missing_eaep")),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        if (autoMergeButton.visible && isPointInsideButton(autoMergeButton, mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    List.of(Component.translatable("gui.ae2utility.recipe_tree.overview_merge_tooltip")),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        if (autoUniqueRecipeButton.visible && isPointInsideButton(autoUniqueRecipeButton, mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    List.of(Component.translatable("gui.ae2utility.recipe_tree.overview_auto_unique_tooltip")),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        if (computeQuantitiesButton.visible && isPointInsideButton(computeQuantitiesButton, mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    List.of(Component.translatable("gui.ae2utility.recipe_tree.overview_quantity_compute_tooltip")),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        if (toggleExistingPatternButton.visible && isPointInsideButton(toggleExistingPatternButton, mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    List.of(Component.literal(context.disableExistingPatternExpansion()
                            ? Component.translatable("gui.ae2utility.recipe_tree.overview_toggle_existing_disabled").getString()
                            : Component.translatable("gui.ae2utility.recipe_tree.overview_toggle_existing_enabled").getString())),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        for (AlternativeButtonBounds bounds : alternativeButtonBounds) {
            if (bounds.contains(logicalMouseX, logicalMouseY)) {
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable("gui.ae2utility.recipe_tree.alternative_button_hint")),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
        }
        for (TopMaterialBounds bounds : topMaterialBounds) {
            if (!bounds.contains(logicalMouseX, logicalMouseY) || bounds.material().alternatives().isEmpty()) {
                continue;
            }
            ItemStack stack = bounds.material().alternatives().get(0);
            List<Component> lines = new ArrayList<>();
            lines.add(stack.getHoverName());
            if (computeRecipeQuantities) {
                lines.add(Component.translatable("gui.ae2utility.recipe_tree.amount_exact", bounds.material().count())
                        .withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable("gui.ae2utility.recipe_tree.material_right_click_hint")
                    .withStyle(ChatFormatting.GRAY));
            if (shouldShowTopMaterialPatternHint(bounds.material())) {
                lines.add(Component.translatable("gui.ae2utility.recipe_tree.me_pattern_exists_hint").withStyle(ChatFormatting.RED));
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
                graphics.renderTooltip(this.font,
                        List.of(Component.translatable("gui.ae2utility.recipe_tree.collapse_branch")),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
            if (logicalMouseX < x || logicalMouseX > x + width || logicalMouseY < y || logicalMouseY > y + NODE_HEIGHT) {
                continue;
            }
            if (node.graph().mergedLeaf() != null && shouldShowMeExistingPatternHints()
                    && shouldBlockExpansion(node.graph().mergedLeaf())) {
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
                if (node.graph().recipeNode() != null && !hasJeiRecipes(node.graph().recipeNode())) {
                    lines.add(Component.translatable("gui.ae2utility.recipe_tree.no_jei_recipe").withStyle(ChatFormatting.AQUA));
                } else if (node.graph().mergedLeaf() != null && !hasJeiRecipes(node.graph().mergedLeaf())) {
                    lines.add(Component.translatable("gui.ae2utility.recipe_tree.no_jei_recipe").withStyle(ChatFormatting.AQUA));
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
        if (button == 0 && handlePatternSubstitutionToggleClick(mouseX, mouseY)) {
            return true;
        }
        if (autoMergeSameMaterials) {
            if (pendingAlternativeSelection != null) {
                for (AlternativeOptionBounds option : alternativeOptionBounds) {
                    if (option.contains(mouseX, mouseY)) {
                        selectAlternative(option.index());
                        return true;
                    }
                }
                pendingAlternativeSelection = null;
            }
            double logicalMouseX = (mouseX - panX) / zoom;
            double logicalMouseY = (mouseY - panY) / zoom;
            if (button == 0) {
                for (AlternativeButtonBounds bounds : alternativeButtonBounds) {
                    if (bounds.contains(logicalMouseX, logicalMouseY)) {
                        openAlternativeSelection(bounds);
                        return true;
                    }
                }
            }
            if (button == 1) {
                TopMaterialBounds materialTarget = findTopMaterialAt(logicalMouseX, logicalMouseY);
                if (materialTarget != null) {
                    jumpToMaterial(materialTarget.material());
                    return true;
                }
            }
            if (button == 0) {
                TopMaterialBounds materialTarget = findTopMaterialAt(logicalMouseX, logicalMouseY);
                if (materialTarget != null) {
                    MergedLeaf leaf = findLeafForMaterial(materialTarget.material());
                    if (leaf != null) {
                        openSelectionWithJei(leaf);
                    }
                    return true;
                }
            }
            LayerMaterialBounds clicked = findLayerAt(logicalMouseX, logicalMouseY);
            if (clicked != null && handleMergedLayerMaterialClick(clicked.material())) {
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (pendingAlternativeSelection != null) {
            for (AlternativeOptionBounds option : alternativeOptionBounds) {
                if (option.contains(mouseX, mouseY)) {
                    selectAlternative(option.index());
                    return true;
                }
            }
            pendingAlternativeSelection = null;
        }
        if (button == 1) {
            double logicalMouseX = (mouseX - panX) / zoom;
            double logicalMouseY = (mouseY - panY) / zoom;
            TopMaterialBounds materialTarget = findTopMaterialAt(logicalMouseX, logicalMouseY);
            if (materialTarget != null) {
                jumpToMaterial(materialTarget.material());
                return true;
            }
        }
        if (button == 0) {
            double logicalMouseX = (mouseX - panX) / zoom;
            double logicalMouseY = (mouseY - panY) / zoom;
            TopMaterialBounds materialTarget = findTopMaterialAt(logicalMouseX, logicalMouseY);
            if (materialTarget != null) {
                MergedLeaf leaf = findLeafForMaterial(materialTarget.material());
                if (leaf != null) {
                    openSelectionWithJei(leaf);
                }
                return true;
            }
        }
        if (button == 0 || button == 1) {
            double logicalMouseX = (mouseX - panX) / zoom;
            double logicalMouseY = (mouseY - panY) / zoom;
            if (button == 0) {
                for (AlternativeButtonBounds bounds : alternativeButtonBounds) {
                    if (bounds.contains(logicalMouseX, logicalMouseY)) {
                        openAlternativeSelection(bounds);
                        return true;
                    }
                }
            }
            if (button == 0) {
                PositionedNode collapseTarget = findCollapseButtonAt(logicalMouseX, logicalMouseY);
                if (collapseTarget != null) {
                    collapseNode(collapseTarget);
                    closeSelection();
                    rebuildLayout();
                    return true;
                }
            }
            PositionedNode clicked = findNodeAt(logicalMouseX, logicalMouseY);
            if (clicked != null) {
                if (clicked.graph().recipeNode() != null) {
                    openSelectionWithJei(clicked.graph().recipeNode());
                    return true;
                }
                if (clicked.graph().mergedLeaf() != null) {
                    if (shouldBlockExpansion(clicked.graph().mergedLeaf())) {
                        return true;
                    }
                    openSelectionWithJei(clicked.graph().mergedLeaf());
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

    private @Nullable MergedLeaf findLeafForMaterial(RequestedIngredient material) {
        String targetSignature = signatureOf(material);
        for (PositionedNode node : positionedNodes) {
            MergedLeaf leaf = node.graph().mergedLeaf();
            if (leaf != null && signatureOf(leaf.representative().requestedIngredient()).equals(targetSignature)) {
                return leaf;
            }
        }
        return findLeafForMaterial(context.root(), targetSignature);
    }

    private @Nullable MergedLeaf findLeafForMaterial(RecipeTreeNodeViewModel node, String targetSignature) {
        List<RecipeTreeInputViewModel> matches = new ArrayList<>();
        RecipeTreeNodeViewModel parent = collectLeafInputsBySignature(node, targetSignature, matches);
        if (parent == null || matches.isEmpty()) {
            return null;
        }
        RecipeTreeInputViewModel representative = matches.getFirst();
        int totalAmount = 0;
        for (RecipeTreeInputViewModel match : matches) {
            totalAmount = safeAdd(totalAmount, Math.max(1, match.amount()));
        }
        return new MergedLeaf(representative.displayIngredient(), displayNameOf(representative), totalAmount,
                representative.amountText(), parent, List.copyOf(matches), List.of());
    }

    private @Nullable RecipeTreeNodeViewModel collectLeafInputsBySignature(RecipeTreeNodeViewModel node, String targetSignature,
            List<RecipeTreeInputViewModel> matches) {
        RecipeTreeNodeViewModel matchedParent = null;
        for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
            RecipeTreeNodeViewModel child = input.child();
            if (child == null) {
                if (signatureOf(input).equals(targetSignature)) {
                    matches.add(input);
                    if (matchedParent == null) {
                        matchedParent = node;
                    }
                }
                continue;
            }
            RecipeTreeNodeViewModel childParent = collectLeafInputsBySignature(child, targetSignature, matches);
            if (matchedParent == null) {
                matchedParent = childParent;
            }
        }
        return matchedParent;
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
        if (autoMergeSameMaterials) {
            if (Screen.hasControlDown()) {
                double oldZoom = zoom;
                zoom = Math.max(0.2, Math.min(4.0, zoom + scrollY * 0.1));
                double logicalX = (mouseX - panX) / oldZoom;
                double logicalY = (mouseY - panY) / oldZoom;
                panX = mouseX - logicalX * zoom;
                panY = mouseY - logicalY * zoom;
                return true;
            }
            if (pendingAlternativeSelection != null && isInsideAlternativeViewport(mouseX, mouseY)
                    && getAlternativeOptionCount() > getAlternativeVisibleCount()) {
                alternativeScroll -= (int) Math.signum(scrollY);
                clampAlternativeScroll();
                return true;
            }
            if (Screen.hasShiftDown()) {
                panX += scrollY * 18;
            } else {
                panY += scrollY * 18;
            }
            return true;
        }
        if (pendingAlternativeSelection != null && isInsideAlternativeViewport(mouseX, mouseY)
                && getAlternativeOptionCount() > getAlternativeVisibleCount()) {
            alternativeScroll -= (int) Math.signum(scrollY);
            clampAlternativeScroll();
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
        if (button == 0) {
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
        pendingJeiSelection = null;
        pendingAlternativeSelection = null;
        this.minecraft.setScreen(returnScreen);
    }

    private boolean containsRecipeInAncestors(@Nullable RecipeTreeNodeViewModel node, RecipeTreeRecipeViewModel candidate) {
        for (RecipeTreeNodeViewModel cursor = node; cursor != null; cursor = cursor.parent()) {
            if (cursor.recipe().sameRecipeAs(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void applyRecipeBatchSelection(List<RecipeTreeNodeViewModel> batch, RecipeTreeRecipeViewModel selected) {
        for (RecipeTreeNodeViewModel targetNode : batch) {
            if (containsRecipeInAncestors(targetNode.parent(), selected)) {
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    player.displayClientMessage(Component.translatable("message.ae2utility.recipe_tree_recursive_recipe")
                            .withStyle(ChatFormatting.RED), true);
                }
                return;
            }
        }
        for (RecipeTreeNodeViewModel targetNode : batch) {
            targetNode.setRecipe(selected);
            autoApplyRememberedChildren(targetNode);
        }
        pendingJeiSelection = null;
    }

    private void applySelectedRecipe(@Nullable RecipeTreeNodeViewModel targetNode, @Nullable MergedLeaf targetLeaf,
            RecipeTreeRecipeViewModel selected) {
        if (targetNode != null) {
            if (containsRecipeInAncestors(targetNode.parent(), selected)) {
                var player = Minecraft.getInstance().player;
                if (player != null) {
                    player.displayClientMessage(Component.translatable("message.ae2utility.recipe_tree_recursive_recipe")
                            .withStyle(ChatFormatting.RED), true);
                }
                return;
            }
            targetNode.setRecipe(selected);
            autoApplyRememberedChildren(targetNode);
        } else if (targetLeaf != null) {
            for (int i = 0; i < targetLeaf.members().size(); i++) {
                if (targetLeaf.parentForMember(i).containsRecipe(selected)) {
                    var player = Minecraft.getInstance().player;
                    if (player != null) {
                        player.displayClientMessage(Component.translatable("message.ae2utility.recipe_tree_recursive_recipe")
                                .withStyle(ChatFormatting.RED), true);
                    }
                    return;
                }
            }
            applyLeafSelection(targetLeaf, selected);
        }
        pendingJeiSelection = null;
        rebuildLayout();
    }

    private boolean openSelectionWithJei(RecipeTreeNodeViewModel targetNode) {
        ITypedIngredient<?> ingredient = targetNode.recipe().primaryOutputIngredient();
        if (ingredient == null || RecipeTreeJeiLookup.findRecipesByOutput(ingredient).isEmpty()) {
            return false;
        }
        pendingJeiSelection = new PendingJeiSelection(targetNode, null, List.of());
        this.minecraft.setScreen(new RecipeTreeJeiBridgeScreen(this, this, ingredient));
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.ae2utility.recipe_tree_opened_jei")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return true;
    }

    private boolean openSelectionWithJei(MergedLeaf targetLeaf) {
        ITypedIngredient<?> ingredient = getJeiSelectionIngredient(targetLeaf.representative());
        if (ingredient == null || RecipeTreeJeiLookup.findRecipesByOutput(ingredient).isEmpty()) {
            return false;
        }
        pendingJeiSelection = new PendingJeiSelection(null, targetLeaf, List.of());
        this.minecraft.setScreen(new RecipeTreeJeiBridgeScreen(this, this, ingredient));
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.ae2utility.recipe_tree_opened_jei")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return true;
    }

    private boolean openRecipeTargetsSelectionWithJei(List<RecipeTreeNodeViewModel> targets) {
        if (targets.isEmpty()) {
            return false;
        }
        RecipeTreeNodeViewModel first = targets.getFirst();
        ITypedIngredient<?> ingredient = first.recipe().primaryOutputIngredient();
        if (ingredient == null || RecipeTreeJeiLookup.findRecipesByOutput(ingredient).isEmpty()) {
            return false;
        }
        pendingJeiSelection = new PendingJeiSelection(first, null, List.copyOf(targets));
        this.minecraft.setScreen(new RecipeTreeJeiBridgeScreen(this, this, ingredient));
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.ae2utility.recipe_tree_opened_jei")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return true;
    }

    private boolean handleMergedLayerMaterialClick(LayerMaterial material) {
        if (!material.recipeTargets().isEmpty()) {
            return openRecipeTargetsSelectionWithJei(material.recipeTargets());
        }
        if (!material.leafInputs().isEmpty()) {
            List<RecipeTreeNodeViewModel> expandedTargets = new ArrayList<>();
            for (RecipeTreeInputViewModel input : material.leafInputs()) {
                RecipeTreeNodeViewModel child = input.child();
                if (child != null && !expandedTargets.contains(child)) {
                    expandedTargets.add(child);
                }
            }
            if (!expandedTargets.isEmpty()) {
                return openRecipeTargetsSelectionWithJei(expandedTargets);
            }
            if (!material.hasUnresolvedLeaves()) {
                return false;
            }
            MergedLeaf leaf = mergedLeafFromLayerMaterial(material);
            if (leaf == null) {
                return false;
            }
            if (shouldBlockExpansion(leaf)) {
                return true;
            }
            return openSelectionWithJei(leaf);
        }
        return false;
    }

    private @Nullable ITypedIngredient<?> getJeiSelectionIngredient(RecipeTreeInputViewModel input) {
        ITypedIngredient<?> ingredient = input.displayIngredient();
        if (ingredient != null) {
            return ingredient;
        }
        ItemStack stack = input.displayStack();
        if (stack.isEmpty()) {
            return null;
        }
        IIngredientManager ingredientManager = getIngredientManager();
        if (ingredientManager == null) {
            return null;
        }
        return ingredientManager.createTypedIngredient(stack.copyWithCount(1), true).orElse(null);
    }

    @Override
    public void ae2utility$applyJeiRecipe(Object recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView recipeSlots) {
        if (pendingJeiSelection == null) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.ae2utility.recipe_tree_select_target_first")
                        .withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        RecipeTreeRecipeViewModel selected = RecipeTreeJeiLookup.createRootSnapshot(recipe, recipeSlots);
        List<RecipeTreeNodeViewModel> batch = pendingJeiSelection.recipeApplyBatch();
        if (!batch.isEmpty()) {
            applyRecipeBatchSelection(batch, selected);
            pendingJeiSelection = null;
            rebuildLayout();
            return;
        }
        applySelectedRecipe(pendingJeiSelection.targetNode(), pendingJeiSelection.targetLeaf(), selected);
    }

    private void applyLeafSelection(MergedLeaf target, RecipeTreeRecipeViewModel selected) {
        context.rememberSelection(signatureOf(target.representative()), selected);
        Map<RecipeTreeNodeViewModel, List<RecipeTreeInputViewModel>> byParent = new LinkedHashMap<>();
        for (int i = 0; i < target.members().size(); i++) {
            RecipeTreeInputViewModel input = target.members().get(i);
            RecipeTreeNodeViewModel owner = target.parentForMember(i);
            byParent.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(input);
        }
        for (Map.Entry<RecipeTreeNodeViewModel, List<RecipeTreeInputViewModel>> entry : byParent.entrySet()) {
            RecipeTreeNodeViewModel childNode = new RecipeTreeNodeViewModel(selected, entry.getKey());
            for (RecipeTreeInputViewModel input : entry.getValue()) {
                input.setChild(childNode);
            }
            autoApplyRememberedChildren(childNode);
        }
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
        var requested = input.requestedIngredientView();
        if (requested != null && !requested.alternatives().isEmpty()) {
            String signature = input.requestedIngredientSignature();
            if (signature != null && !signature.isBlank()) {
                return signature;
            }
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

    private String displayNameOfOutput(RecipeTreeNodeViewModel node) {
        return node.recipe().title().getString();
    }

    private String signatureOfNode(RecipeTreeNodeViewModel node) {
        return signatureOf(node);
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
        this.pendingJeiSelection = null;
        this.pendingAlternativeSelection = null;
        this.alternativeScroll = 0;
        this.alternativeOptionBounds.clear();
        updateSelectionButtons();
    }

    private void collapseNode(PositionedNode positionedNode) {
        RecipeTreeNodeViewModel node = positionedNode.graph().recipeNode();
        if (node == null || node.parent() == null) {
            return;
        }
        collapseNodesMatchingSignature(context.root(), signatureOf(node));
    }

    private void collapseNodesMatchingSignature(RecipeTreeNodeViewModel parent, String targetSignature) {
        for (RecipeTreeInputViewModel input : parent.recipe().inputs()) {
            RecipeTreeNodeViewModel child = input.child();
            if (child == null) {
                continue;
            }
            if (signatureOf(child).equals(targetSignature)) {
                context.forgetSelection(signatureOf(input));
                input.setChild(null);
                continue;
            }
            collapseNodesMatchingSignature(child, targetSignature);
        }
    }

    private void jumpToMaterial(RequestedIngredient material) {
        String targetSignature = signatureOf(material);
        if (autoMergeSameMaterials) {
            jumpToMaterialMergedLayers(targetSignature);
            return;
        }
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

    private boolean layerMaterialMatchesSignature(LayerMaterial mat, String targetSignature) {
        for (RecipeTreeInputViewModel input : mat.leafInputs()) {
            if (signatureOf(input).equals(targetSignature)) {
                return true;
            }
        }
        for (RecipeTreeNodeViewModel node : mat.recipeTargets()) {
            if (signatureOf(node).equals(targetSignature)) {
                return true;
            }
        }
        return false;
    }

    private void jumpToMaterialMergedLayers(String targetSignature) {
        int bestDepth = Integer.MAX_VALUE;
        for (int d = 0; d < mergedLayerRows.size(); d++) {
            for (LayerMaterial mat : mergedLayerRows.get(d).materials()) {
                if (layerMaterialMatchesSignature(mat, targetSignature)) {
                    bestDepth = Math.min(bestDepth, d);
                    break;
                }
            }
        }
        if (bestDepth == Integer.MAX_VALUE) {
            return;
        }
        double targetLogicalY = 42 + TOP_MATERIALS_OFFSET + bestDepth * LEVEL_GAP + NODE_HEIGHT / 2.0D;
        panY = this.height * 0.45D - targetLogicalY * zoom;
    }

    private boolean hasExistingPatternForLeaf(MergedLeaf leaf) {
        RecipeTreeInputViewModel rep = leaf.representative();
        ITypedIngredient<?> disp = rep.displayIngredient();
        if (disp != null) {
            AEKey key = GenericIngredientUtil.toAEKey(disp.getIngredient());
            return key != null && CraftableStateCache.isCraftable(key);
        }
        return hasExistingPatternForRequestedIngredient(rep.requestedIngredient());
    }

    private boolean hasJeiRecipes(RecipeTreeNodeViewModel node) {
        ITypedIngredient<?> ingredient = node.recipe().primaryOutputIngredient();
        return ingredient != null && !RecipeTreeJeiLookup.findRecipesByOutput(ingredient).isEmpty();
    }

    private boolean hasJeiRecipes(MergedLeaf leaf) {
        ITypedIngredient<?> ingredient = getJeiSelectionIngredient(leaf.representative());
        return ingredient != null && !RecipeTreeJeiLookup.findRecipesByOutput(ingredient).isEmpty();
    }

    private List<RecipeTreeInputViewModel> collectUnresolvedInputsBySignature(RecipeTreeNodeViewModel node, String targetSignature) {
        List<RecipeTreeInputViewModel> matches = new ArrayList<>();
        collectUnresolvedInputsBySignature(node, targetSignature, matches);
        return matches;
    }

    private void collectUnresolvedInputsBySignature(RecipeTreeNodeViewModel node, String targetSignature, List<RecipeTreeInputViewModel> matches) {
        for (RecipeTreeInputViewModel input : node.recipe().inputs()) {
            RecipeTreeNodeViewModel child = input.child();
            if (child == null) {
                if (signatureOf(input).equals(targetSignature)) {
                    matches.add(input);
                }
                continue;
            }
            collectUnresolvedInputsBySignature(child, targetSignature, matches);
        }
    }

    /** 仅检查首个非空备选，避免 tag 膨胀时对 {@link CraftableStateCache} 的风暴（与 JEI「当前展示」语义一致）。 */
    private boolean hasExistingPatternForRequestedIngredient(@Nullable RequestedIngredient ingredient) {
        if (ingredient == null) {
            return false;
        }
        for (ItemStack alternative : ingredient.alternatives()) {
            if (alternative.isEmpty()) {
                continue;
            }
            AEKey key = GenericIngredientUtil.toAEKey(alternative);
            return key != null && CraftableStateCache.isCraftable(key);
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
        computeQuantitiesButton.visible = true;
        computeQuantitiesButton.active = true;
        toggleExistingPatternButton.visible = true;
        toggleExistingPatternButton.active = true;
        autoUniqueRecipeButton.visible = true;
        autoUniqueRecipeButton.active = true;
        autoMergeButton.visible = true;
        autoMergeButton.active = true;
        encodeButton.visible = true;
        encodeButton.active = true;
        uploadButton.visible = true;
        uploadButton.active = EaepCompat.isExtendedAePlusLoaded();
        backButton.visible = true;
        backButton.active = true;
        backButton.setX(this.width - 68);
        backButton.setY(10);
        computeQuantitiesButton.setX(this.width - 356);
        computeQuantitiesButton.setY(10);
        toggleExistingPatternButton.setX(this.width - 212);
        toggleExistingPatternButton.setY(10);
        autoUniqueRecipeButton.setX(this.width - 284);
        autoUniqueRecipeButton.setY(10);
        autoMergeButton.setX(this.width - 140);
        autoMergeButton.setY(10);
        encodeButton.setX(this.width / 2 - 62);
        encodeButton.setY(this.height - 26);
        uploadButton.setX(this.width / 2 + 4);
        uploadButton.setY(this.height - 26);
        syncComputeQuantitiesButton();
        syncToggleExistingPatternButton();
        syncAutoUniqueRecipeButton();
        syncAutoMergeButton();
    }

    private void renderAlternativeSelection(GuiGraphics graphics, int mouseX, int mouseY) {
        alternativeOptionBounds.clear();
        if (pendingAlternativeSelection == null) {
            return;
        }

        List<DisplayOption> alternatives = pendingAlternativeSelection.alternatives();
        if (alternatives.isEmpty()) {
            pendingAlternativeSelection = null;
            return;
        }

        int visibleCount = getAlternativeVisibleCount();
        int panelWidth = 148;
        int panelHeight = visibleCount * 18 + 6;
        int panelX = Math.max(6, Math.min(this.width - panelWidth - 6, pendingAlternativeSelection.anchorX() + 12));
        int panelY = Math.max(6, Math.min(this.height - panelHeight - 6, pendingAlternativeSelection.anchorY() - 4));
        fillFramedPanel(graphics, panelX, panelY, panelX + panelWidth, panelY + panelHeight,
                0xFF30373D, 0xFF4D5962, 0xFFE8EEF2);

        clampAlternativeScroll();
        int start = alternativeScroll;
        int end = Math.min(alternatives.size(), start + visibleCount);
        for (int i = start; i < end; i++) {
            int optionY = panelY + 4 + (i - start) * 18;
            DisplayOption option = alternatives.get(i);
            boolean selected = i == pendingAlternativeSelection.selectedAlternativeIndex();
            if (selected) {
                graphics.fill(panelX + 3, optionY, panelX + panelWidth - 3, optionY + 17, 0x3358A6FF);
            }
            renderTypedSlot(graphics, panelX + 4, optionY, option.typedIngredient());
            graphics.drawString(this.font, trimToWidth(Component.literal(option.label()), panelWidth - 28),
                    panelX + 24, optionY + 5, 0xFF263238, false);
            alternativeOptionBounds.add(new AlternativeOptionBounds(i, option.typedIngredient(), panelX + 3, optionY, panelWidth - 6, 17));
        }
    }

    private void openAlternativeSelection(AlternativeButtonBounds bounds) {
        List<RecipeTreeInputViewModel> members;
        if (bounds.leaf() != null) {
            members = bounds.leaf().members();
        } else if (bounds.material() != null) {
            members = collectUnresolvedInputsBySignature(context.root(), signatureOf(bounds.material()));
        } else {
            members = List.of();
        }
        if (members.isEmpty()) {
            pendingAlternativeSelection = null;
            return;
        }
        RecipeTreeInputViewModel representative = members.get(0);
        int anchorX = (int) Math.round(bounds.x() * zoom + panX);
        int anchorY = (int) Math.round(bounds.y() * zoom + panY);
        pendingAlternativeSelection = new PendingAlternativeSelection(List.copyOf(members), representative.displayOptions(),
                representative.selectedAlternativeIndex(), anchorX, anchorY);
        alternativeScroll = 0;
    }

    private void selectAlternative(int index) {
        if (pendingAlternativeSelection == null) {
            return;
        }
        for (RecipeTreeInputViewModel member : pendingAlternativeSelection.members()) {
            member.selectAlternative(index);
        }
        pendingAlternativeSelection = null;
        rebuildLayout();
    }

    private int getAlternativeVisibleCount() {
        return 8;
    }

    private int getAlternativeOptionCount() {
        return pendingAlternativeSelection == null ? 0 : pendingAlternativeSelection.alternatives().size();
    }

    private void clampAlternativeScroll() {
        int maxScroll = Math.max(0, getAlternativeOptionCount() - getAlternativeVisibleCount());
        alternativeScroll = Math.max(0, Math.min(maxScroll, alternativeScroll));
    }

    private boolean isInsideAlternativeViewport(double mouseX, double mouseY) {
        if (pendingAlternativeSelection == null) {
            return false;
        }
        int panelWidth = 148;
        int panelHeight = getAlternativeVisibleCount() * 18 + 6;
        int panelX = Math.max(6, Math.min(this.width - panelWidth - 6, pendingAlternativeSelection.anchorX() + 12));
        int panelY = Math.max(6, Math.min(this.height - panelHeight - 6, pendingAlternativeSelection.anchorY() - 4));
        return mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= panelY && mouseY <= panelY + panelHeight;
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

    private void renderVanillaIngredientTooltip(GuiGraphics graphics, ITypedIngredient<?> ingredient, int mouseX, int mouseY) {
        ItemStack stack = extractItemStack(ingredient);
        if (!stack.isEmpty()) {
            graphics.renderTooltip(this.font, stack, mouseX, mouseY);
            return;
        }
        IIngredientManager ingredientManager = getIngredientManager();
        if (ingredientManager == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        ITypedIngredient<Object> typed = (ITypedIngredient<Object>) ingredient;
        IIngredientRenderer<Object> renderer = ingredientManager.getIngredientRenderer(typed.getType());
        List<Component> tooltip = renderer.getTooltip(typed.getIngredient(), TooltipFlag.Default.NORMAL);
        if (!tooltip.isEmpty()) {
            graphics.renderTooltip(this.font, tooltip, Optional.empty(), mouseX, mouseY);
        }
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

    private void syncComputeQuantitiesButton() {
        computeQuantitiesButton.setMessage(Component.translatable(computeRecipeQuantities
                ? "gui.ae2utility.recipe_tree.overview_quantity_compute_enabled"
                : "gui.ae2utility.recipe_tree.overview_quantity_compute_disabled"));
    }

    private void syncToggleExistingPatternButton() {
        toggleExistingPatternButton.setMessage(Component.literal(
                context.disableExistingPatternExpansion() ? "已有样板:禁" : "已有样板:开"));
    }

    private void syncAutoUniqueRecipeButton() {
        autoUniqueRecipeButton.setMessage(Component.translatable(autoExpandUniqueEncodableRecipe
                ? "gui.ae2utility.recipe_tree.overview_auto_unique_enabled"
                : "gui.ae2utility.recipe_tree.overview_auto_unique_disabled"));
    }

    private void syncAutoMergeButton() {
        autoMergeButton.setMessage(autoMergeSameMaterials
                ? Component.translatable("gui.ae2utility.recipe_tree.overview_merge_enabled")
                : Component.translatable("gui.ae2utility.recipe_tree.overview_merge_disabled"));
    }

    private void markAutoExpandUniqueDirty() {
        autoExpandUniqueSearchPending = autoExpandUniqueEncodableRecipe;
        autoExpandUniqueCandidateCache.clear();
    }

    private boolean stopBatchEncodeIfLocallyNoDetectableBlank() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        if (BlankPatternClientPrecheck.lacksAnyDetectableBlankPattern(player)) {
            player.displayClientMessage(Component.translatable("message.ae2utility.batch_encode_precheck_no_blank").withStyle(ChatFormatting.GOLD),
                    false);
            return true;
        }
        return false;
    }

    private void uploadPatterns() {
        if (!EaepCompat.isExtendedAePlusLoaded()) {
            return;
        }
        int bulkSid = BulkEncodeSessions.next();
        List<EncodePatternPacket> packets = collectEncodablePackets(true, bulkSid);
        if (packets.isEmpty()) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.ae2utility.recipe_tree_no_patterns"), true);
            }
            return;
        }
        int origCount = packets.size();
        packets = new ArrayList<>(RemoteEncodeRules.capPacketsToServerBulkLimit(packets));
        if (packets.size() < origCount && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("message.ae2utility.bulk_encode_truncated_client_notice", origCount, packets.size()).withStyle(ChatFormatting.GOLD),
                    false);
        }
        if (stopBatchEncodeIfLocallyNoDetectableBlank()) {
            return;
        }
        RecipeTreeUploadQueue.startReplacing(packets);
        com.lhy.ae2utility.jei.JeiEncodePacketFactory.sendEaepProviderRefreshIfNeeded(true);
        this.onClose();
    }

    private void encodePatterns() {
        int bulkSid = BulkEncodeSessions.next();
        List<EncodePatternPacket> packets = collectEncodablePackets(false, bulkSid);
        if (packets.isEmpty()) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.ae2utility.recipe_tree_no_patterns"), true);
            }
            return;
        }
        int origCount = packets.size();
        packets = new ArrayList<>(RemoteEncodeRules.capPacketsToServerBulkLimit(packets));
        if (packets.size() < origCount && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("message.ae2utility.bulk_encode_truncated_client_notice", origCount, packets.size()).withStyle(ChatFormatting.GOLD),
                    false);
        }
        if (stopBatchEncodeIfLocallyNoDetectableBlank()) {
            return;
        }
        for (EncodePatternPacket packet : packets) {
            PacketDistributor.sendToServer(packet);
        }
        this.onClose();
    }

    private List<EncodePatternPacket> collectEncodablePackets(boolean uploadMode, int bulkEncodeSessionId) {
        List<EncodePatternPacket> packets = new ArrayList<>();
        for (RecipeTreeRecipeViewModel recipe : context.collectSelectedRecipes()) {
            if (hasExistingPatternForOutput(recipe)) {
                continue;
            }
            EncodePatternPacket packet = toEncodePacket(recipe);
            if (packet != null) {
                if (uploadMode) {
                    packets.add(new EncodePatternPacket(packet.inputs(), packet.outputs(), packet.recipeId(), packet.patternName(),
                            packet.providerSearchKey(), packet.providerDisplayName(), true, packet.substitute(), packet.substituteFluids(), true, true,
                            false, bulkEncodeSessionId));
                } else {
                    packets.add(packet.withBulkEncodeSessionId(bulkEncodeSessionId));
                }
            }
        }
        return packets;
    }

    private @Nullable EncodePatternPacket toEncodePacket(RecipeTreeRecipeViewModel recipe) {
        GenericStack output = toGenericStack(recipe.primaryOutputIngredient(), recipe.primaryOutputCount());
        if (output == null) {
            return null;
        }

        List<List<GenericStack>> inputs = new ArrayList<>();
        for (RecipeTreeInputViewModel input : recipe.inputs()) {
            List<GenericStack> alternatives = new ArrayList<>();
            RequestedIngredient requested = input.selectedRequestedIngredient();
            if (requested != null) {
                for (ItemStack alternative : requested.alternatives()) {
                    if (!alternative.isEmpty()) {
                        alternatives.add(GenericStack.fromItemStack(alternative.copyWithCount(Math.max(1, requested.count()))));
                    }
                }
            } else {
                for (DisplayOption option : input.orderedDisplayOptions()) {
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
                deriveProviderSearchKey(recipe), deriveProviderDisplayName(recipe), false,
                JeiPatternSubstitutionUi.isItemSubstituteOn(), JeiPatternSubstitutionUi.isFluidSubstituteOn(), true);
    }

    private static String deriveProviderSearchKey(RecipeTreeRecipeViewModel recipe) {
        if (!EaepCompat.isExtendedAePlusLoaded()) {
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

    private int computeRequiredPatternCountUncached(List<RecipeTreeRecipeViewModel> selectedRecipes) {
        int count = 0;
        for (RecipeTreeRecipeViewModel recipe : selectedRecipes) {
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

    private void collapseExpandedExistingPatternNodes(RecipeTreeNodeViewModel parent) {
        for (RecipeTreeInputViewModel input : parent.recipe().inputs()) {
            RecipeTreeNodeViewModel child = input.child();
            if (child == null) {
                continue;
            }
            if (hasExistingPatternForOutput(child.recipe())) {
                context.forgetSelection(signatureOf(input));
                input.setChild(null);
                continue;
            }
            collapseExpandedExistingPatternNodes(child);
        }
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
            RecipeTreeNodeViewModel parentNode, List<RecipeTreeInputViewModel> members,
            List<RecipeTreeNodeViewModel> memberParents) {
        private MergedLeaf {
            memberParents = memberParents == null ? List.of() : List.copyOf(memberParents);
        }

        private RecipeTreeNodeViewModel parentForMember(int index) {
            if (!memberParents.isEmpty() && index < memberParents.size()) {
                return memberParents.get(index);
            }
            return parentNode;
        }

        private MergedLeaf withAddedAmount(int addedAmount, RecipeTreeInputViewModel member) {
            List<RecipeTreeInputViewModel> nextMembers = new ArrayList<>(members);
            nextMembers.add(member);
            List<RecipeTreeNodeViewModel> nextParents = new ArrayList<>(memberParents);
            if (!nextParents.isEmpty()) {
                nextParents.add(parentNode);
            }
            return new MergedLeaf(ingredient, title, safeAdd(totalAmount, Math.max(1, addedAmount)), sourceAmountText, parentNode,
                    List.copyOf(nextMembers), List.copyOf(nextParents));
        }

        private RecipeTreeInputViewModel representative() {
            return members.getFirst();
        }
    }

    private record PositionedNode(GraphNode graph, int x, int y) {
    }

    private record Edge(PositionedNode parent, PositionedNode child) {
    }

    private record PendingJeiSelection(@Nullable RecipeTreeNodeViewModel targetNode, @Nullable MergedLeaf targetLeaf,
            List<RecipeTreeNodeViewModel> recipeApplyBatch) {
        private PendingJeiSelection {
            recipeApplyBatch = recipeApplyBatch == null ? List.of() : List.copyOf(recipeApplyBatch);
        }
    }

    private record PendingAlternativeSelection(List<RecipeTreeInputViewModel> members, List<DisplayOption> alternatives,
            int selectedAlternativeIndex, int anchorX, int anchorY) {
    }

    private record TopMaterialBounds(RequestedIngredient material, int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private record AlternativeButtonBounds(@Nullable MergedLeaf leaf, @Nullable RequestedIngredient material, int x, int y, int width, int height) {
        private static AlternativeButtonBounds forLeaf(MergedLeaf leaf, int x, int y, int width, int height) {
            return new AlternativeButtonBounds(leaf, null, x, y, width, height);
        }

        private static AlternativeButtonBounds forMaterial(RequestedIngredient material, int x, int y, int width, int height) {
            return new AlternativeButtonBounds(null, material, x, y, width, height);
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private record AlternativeOptionBounds(int index, @Nullable ITypedIngredient<?> ingredient, int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private static final class MergedBuildStats {
        int collectCalls;
        int recipeNodeAdds;
        int leafAdds;
        int aggregatedChildLinks;
        int layerRows;
        int layerMaterials;
    }
}
