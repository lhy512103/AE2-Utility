package com.lhy.ae2utility.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import com.lhy.ae2utility.menu.RecipeFinderMenu;
import com.lhy.ae2utility.jei.BulkEncodeSessions;
import com.lhy.ae2utility.client.RemoteEncodeRules;
import com.lhy.ae2utility.debug.JeiEncodeQueueDebugLog;
import com.lhy.ae2utility.network.EncodePatternPacket;
import com.lhy.ae2utility.network.RecipeFinderEncodePacket;
import com.lhy.ae2utility.recipe_finder.RecipeFinderCandidateView;
import com.lhy.ae2utility.recipe_finder.RecipeFinderFeatureClassifier;

public class RecipeFinderScreen extends AbstractContainerScreen<RecipeFinderMenu> {

    // --- Window size ---
    private static final int W = 250;
    private static final int H = 296;

    // --- Header: sample slot + action buttons (y = 19..34) ---
    private static final int HDR_Y  = 19;
    private static final int HDR_H  = 16;

    // --- Tab bar (y = 38..52) ---
    private static final int TAB_Y = 38;
    private static final int TAB_H = 15;

    // --- Content area (y = 55..192) ---
    private static final int CONT_Y = 55;
    private static final int CONT_H = 137;

    // Status bar label y (menu-relative, used in renderLabels)
    private static final int STAT_Y   = 194;
    private static final int INV_LBL_Y = 207;
    // invStartY = 212 in RecipeFinderMenu

    // --- Results grid ---
    private static final int GRID_COLS = 13;
    private static final int GRID_ROWS = 6;
    private static final int GRID_SLOT = 18;
    private static final int LST_X   = 8;
    private static final int LST_W   = 234;  // W - 8*2

    // --- Dropdown ---
    private static final int DD_ROW_H      = 12;
    private static final int DD_MAX_ROWS   = 9;

    // --- Filter layout ---
    // fw = half-width of content for filter boxes
    private int fw() { return (LST_W - 4) / 2; }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private int currentTab  = 0;   // 0 = filter, 1 = results
    private int currentPage = 0;

    private final Set<String>   selectedKeys   = new LinkedHashSet<>();
    private final List<Integer> filteredIdxs   = new ArrayList<>();
    private final Map<String,String> modLabels     = new HashMap<>();
    private final Map<String,String> machineLabels = new HashMap<>();

    private List<RecipeFinderCandidateView> allCandidates = List.of();
    private List<RecipeFinderCandidateView> candidates    = List.of();

    private List<String> modOpts      = List.of("all");
    private List<String> machineOpts  = List.of("all");
    private List<String> materialOpts = List.of("all");
    private List<String> outputOpts   = List.of("all");
    private List<String> excludeTagOpts = List.of("all");
    private int modIdx, machineIdx, materialIdx, outputIdx, excludeTagIdx;

    private boolean encodableOnly;
    private String  keyword = "";
    private int     lastSnapVersion = -1;
    private ItemStack lastSample    = ItemStack.EMPTY;
    private String  statusMessage   = "";
    private EditBox keywordBox;
    private String savedModValue = "all";
    private String savedMachineValue = "all";
    private String savedMaterialValue = "all";
    private String savedOutputValue = "all";
    private String savedExcludeTagValue = "all";

    // Dropdown
    private KindField openDropdown      = null;
    private int       ddScrollTop       = 0;
    private int       ddAbsX, ddAbsY, ddAbsW; // absolute screen coords

    // -------------------------------------------------------------------------
    // Constructor / init
    // -------------------------------------------------------------------------
    public RecipeFinderScreen(RecipeFinderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth  = W;
        this.imageHeight = H;
        this.inventoryLabelY = INV_LBL_Y;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        restoreState();
        initKeywordBox();
    }

    private void initKeywordBox() {
        int x = leftPos + LST_X + fw() + 4;
        int y = topPos + CONT_Y + 48;
        int width = fw();
        this.keywordBox = new EditBox(font, x + 3, y + 3, width - 8, 10,
                Component.literal("keyword"));
        this.keywordBox.setBordered(false);
        this.keywordBox.setMaxLength(40);
        this.keywordBox.setValue(keyword);
        this.keywordBox.setResponder(value -> {
            keyword = value;
            currentPage = 0;
            rebuildFiltered();
            saveState();
        });
    }

    private void restoreState() {
        RecipeFinderScreenState.FilterState state = RecipeFinderScreenState.get();
        savedModValue = state.modValue;
        savedMachineValue = state.machineValue;
        savedMaterialValue = state.materialValue;
        savedOutputValue = state.outputValue;
        savedExcludeTagValue = state.excludeTagValue;
        encodableOnly = state.encodableOnly;
        keyword = state.keyword;
    }

    private void saveState() {
        RecipeFinderScreenState.FilterState state = RecipeFinderScreenState.get();
        state.modValue = currentValue(modOpts, modIdx);
        state.machineValue = currentValue(machineOpts, machineIdx);
        state.materialValue = currentValue(materialOpts, materialIdx);
        state.outputValue = currentValue(outputOpts, outputIdx);
        state.excludeTagValue = currentValue(excludeTagOpts, excludeTagIdx);
        state.encodableOnly = encodableOnly;
        state.keyword = keyword;
    }

    // -------------------------------------------------------------------------
    // Data refresh
    // -------------------------------------------------------------------------
    @Override
    public void containerTick() {
        super.containerTick();
        RecipeFinderClientState.Snapshot snap = RecipeFinderClientState.getSnapshot();
        ItemStack sample = menu.getSampleStack();
        boolean snapChanged = snap.version() != lastSnapVersion;
        boolean sampleChanged = !ItemStack.isSameItemSameComponents(lastSample, sample);
        if (snapChanged || sampleChanged) {
            if (snapChanged) {
                lastSnapVersion = snap.version();
                allCandidates = List.copyOf(snap.recipes());
                statusMessage = snap.statusMessage();
            }
            if (sampleChanged) {
                lastSample = sample.copy();
            }
            rebuildCandidatesAndOptions();
        }
    }

    private void rebuildCandidatesAndOptions() {
        rememberSelectedValues();
        modLabels.clear();
        machineLabels.clear();
        candidates = sampleFilter(allCandidates, menu.getSampleStack());
        currentPage = 0;
        modOpts      = buildOpts(KindField.MOD);
        machineOpts  = buildOpts(KindField.MACHINE);
        materialOpts = buildOpts(KindField.MATERIAL);
        outputOpts   = buildOpts(KindField.OUTPUT);
        excludeTagOpts = buildOpts(KindField.EXCLUDE_TAG);
        restoreSelectedValues();
        Set<String> keys = candidates.stream()
                .map(RecipeFinderCandidateView::identityKey).collect(Collectors.toSet());
        selectedKeys.retainAll(keys);
        rebuildFiltered();
    }

    private void rememberSelectedValues() {
        savedModValue = currentValue(modOpts, modIdx);
        savedMachineValue = currentValue(machineOpts, machineIdx);
        savedMaterialValue = currentValue(materialOpts, materialIdx);
        savedOutputValue = currentValue(outputOpts, outputIdx);
        savedExcludeTagValue = currentValue(excludeTagOpts, excludeTagIdx);
    }

    private void restoreSelectedValues() {
        modIdx = indexOfValue(modOpts, savedModValue);
        machineIdx = indexOfValue(machineOpts, savedMachineValue);
        materialIdx = indexOfValue(materialOpts, savedMaterialValue);
        outputIdx = indexOfValue(outputOpts, savedOutputValue);
        excludeTagIdx = indexOfValue(excludeTagOpts, savedExcludeTagValue);
    }

    private int indexOfValue(List<String> options, String value) {
        int idx = options.indexOf(value);
        return idx >= 0 ? idx : 0;
    }

    private String currentValue(List<String> options, int index) {
        if (options.isEmpty()) {
            return "all";
        }
        return options.get(Mth.clamp(index, 0, options.size() - 1));
    }

    /**
     * 样本筛选规则：
     *   A. 配方直接用到了样本物品（输入/输出 itemId 命中样本）→ 强相关，保留
     *   B. 否则，要求"配方涉及样本所属模组"且"特征至少有一个交集"
     *      （这才能算"同模组的同类物品配方"，比如同样是 AE2 的 component）
     * 没有特征时（other 兜底），退化为只按模组匹配，避免命中过多无关配方。
     */
    private List<RecipeFinderCandidateView> sampleFilter(List<RecipeFinderCandidateView> src, ItemStack sample) {
        if (sample == null || sample.isEmpty()) return List.copyOf(src);
        String sampleId = BuiltInRegistries.ITEM.getKey(sample.getItem()).toString();
        String sampleModId = BuiltInRegistries.ITEM.getKey(sample.getItem()).getNamespace();
        Set<String> features = RecipeFinderFeatureClassifier.classifyItemStack(sample).stream()
                .filter(f -> !"other".equals(f))
                .collect(Collectors.toSet());

        return src.stream().filter(c -> {
            // A. 直接命中样本物品
            if (c.outputItemIds().contains(sampleId) || c.inputItemIds().contains(sampleId)) {
                return true;
            }
            // B. 涉及样本所属模组
            boolean sameMod = c.involvedModIds().contains(sampleModId)
                    || c.sourceModId().equals(sampleModId);
            if (!sameMod) return false;
            if (features.isEmpty()) return true;  // 无特征兜底：只按模组
            // 同模组 + 特征交集
            return !Collections.disjoint(c.outputFeatureKeys(), features)
                    || !Collections.disjoint(c.inputFeatureKeys(), features);
        }).toList();
    }

    private List<String> buildOpts(KindField field) {
        LinkedHashSet<String> vals = new LinkedHashSet<>();
        vals.add("all");
        for (RecipeFinderCandidateView c : candidates) {
            switch (field) {
                case MOD -> vals.add(c.sourceModId());
                case MACHINE -> vals.add(c.machineLabel());
                case MATERIAL -> vals.addAll(c.inputFeatureKeys());
                case OUTPUT -> vals.addAll(c.outputFeatureKeys());
                case EXCLUDE_TAG -> {
                    vals.addAll(c.inputFeatureKeys());
                    vals.addAll(c.outputFeatureKeys());
                }
            }
            modLabels.put(c.sourceModId(), c.sourceModName());
            machineLabels.put(c.machineLabel(), c.machineLabel());
        }
        return List.copyOf(vals);
    }

    private void setIdx(KindField field, int idx) {
        switch (field) {
            case MOD      -> modIdx      = idx;
            case MACHINE  -> machineIdx  = idx;
            case MATERIAL -> materialIdx = idx;
            case OUTPUT   -> outputIdx   = idx;
            case EXCLUDE_TAG -> excludeTagIdx = idx;
        }
        currentPage = 0;
        rebuildFiltered();
        saveState();
    }

    private int getIdx(KindField field) {
        return switch (field) {
            case MOD      -> modIdx;
            case MACHINE  -> machineIdx;
            case MATERIAL -> materialIdx;
            case OUTPUT   -> outputIdx;
            case EXCLUDE_TAG -> excludeTagIdx;
        };
    }

    private List<String> getOpts(KindField field) {
        return switch (field) {
            case MOD      -> modOpts;
            case MACHINE  -> machineOpts;
            case MATERIAL -> materialOpts;
            case OUTPUT   -> outputOpts;
            case EXCLUDE_TAG -> excludeTagOpts;
        };
    }

    private void rebuildFiltered() {
        filteredIdxs.clear();
        String mf   = modOpts.get(modIdx);
        String mcf  = machineOpts.get(machineIdx);
        String matf = materialOpts.get(materialIdx);
        String outf = outputOpts.get(outputIdx);
        String exf  = excludeTagOpts.get(excludeTagIdx);
        String kw   = keyword.toLowerCase();
        for (int i = 0; i < candidates.size(); i++) {
            RecipeFinderCandidateView c = candidates.get(i);
            if (!"all".equals(mf)   && !mf.equals(c.sourceModId()))                  continue;
            if (!"all".equals(mcf)  && !mcf.equals(c.machineLabel()))                continue;
            if (!"all".equals(matf) && !c.inputFeatureKeys().contains(matf))          continue;
            if (!"all".equals(outf) && !c.outputFeatureKeys().contains(outf))         continue;
            if (!"all".equals(exf) && (c.inputFeatureKeys().contains(exf) || c.outputFeatureKeys().contains(exf))) {
                continue;
            }
            if (!kw.isEmpty() && !c.displayName().toLowerCase().contains(kw))        continue;
            if (encodableOnly && !c.encodable())                                       continue;
            filteredIdxs.add(i);
        }
        currentPage = Mth.clamp(currentPage, 0, Math.max(0, pageCount() - 1));
    }

    private int pageCount() {
        return Math.max(1, (filteredIdxs.size() + gridPageSize() - 1) / gridPageSize());
    }

    private int gridPageSize() {
        return GRID_COLS * GRID_ROWS;
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------
    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;

        panel(g, x, y, W, H, false);

        // Sample slot (always in header)
        slotBox(g, x + LST_X - 1, y + HDR_Y - 1, 18, 18);

        // Action buttons
        int bw = 51;
        btn(g, x + 30,  y + HDR_Y, bw,    HDR_H, "刷新",    isAt(30,  HDR_Y, bw,    HDR_H, mx, my));
        btn(g, x + 83,  y + HDR_Y, bw,    HDR_H, "编码所选", isAt(83,  HDR_Y, bw,    HDR_H, mx, my));
        btn(g, x + 136, y + HDR_Y, bw,    HDR_H, "清空选择", isAt(136, HDR_Y, bw,    HDR_H, mx, my));
        btn(g, x + 189, y + HDR_Y, bw - 2, HDR_H, "全选可见", isAt(189, HDR_Y, bw - 2, HDR_H, mx, my));

        g.fill(x + 7, y + 37, x + W - 7, y + 38, 0xFF999999);

        // Tab bar
        tab(g, x + 8,   y + TAB_Y, 115, TAB_H, "筛选器",
                currentTab == 0, isAt(8,   TAB_Y, 115, TAB_H, mx, my));
        tab(g, x + 125, y + TAB_Y, 115, TAB_H,
                "结果 (" + filteredIdxs.size() + ")",
                currentTab == 1, isAt(125, TAB_Y, 115, TAB_H, mx, my));

        g.fill(x + 7, y + CONT_Y - 1, x + W - 7, y + CONT_Y, 0xFF999999);

        if (currentTab == 0) renderFilterTab(g, mx, my);
        else                  renderResultsTab(g, mx, my);

        g.fill(x + 7, y + CONT_Y + CONT_H + 1, x + W - 7, y + CONT_Y + CONT_H + 2, 0xFF999999);
        g.fill(x + 7, y + INV_LBL_Y - 4,       x + W - 7, y + INV_LBL_Y - 3,       0xFF999999);

        // Inventory slot backgrounds
        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            if (slot.index == RecipeFinderMenu.SAMPLE_SLOT_INDEX) continue;
            slotBox(g, x + slot.x - 1, y + slot.y - 1, 18, 18);
        }
    }

    private void renderFilterTab(GuiGraphics g, int mx, int my) {
        int cx  = leftPos + LST_X;
        int cy  = topPos  + CONT_Y + 4;
        int fw  = fw();

        // Row 1: Mod | Machine
        filterBox(g, cx,        cy, fw, 16,
                Component.translatable("gui.ae2utility.recipe_finder.mod_filter",
                        optLabel(KindField.MOD, modOpts.get(modIdx))),
                isAt(LST_X, CONT_Y + 4, fw, 16, mx, my));
        filterBox(g, cx + fw + 4, cy, fw, 16,
                Component.translatable("gui.ae2utility.recipe_finder.machine_filter",
                        optLabel(KindField.MACHINE, machineOpts.get(machineIdx))),
                isAt(LST_X + fw + 4, CONT_Y + 4, fw, 16, mx, my));

        cy += 22;
        // Row 2: Material | Output
        filterBox(g, cx,        cy, fw, 16,
                Component.translatable("gui.ae2utility.recipe_finder.material_filter",
                        optLabel(KindField.MATERIAL, materialOpts.get(materialIdx))),
                isAt(LST_X, CONT_Y + 26, fw, 16, mx, my));
        filterBox(g, cx + fw + 4, cy, fw, 16,
                Component.translatable("gui.ae2utility.recipe_finder.output_filter",
                        optLabel(KindField.OUTPUT, outputOpts.get(outputIdx))),
                isAt(LST_X + fw + 4, CONT_Y + 26, fw, 16, mx, my));

        cy += 22;
        filterBox(g, cx, cy, fw, 16,
                Component.translatable("gui.ae2utility.recipe_finder.exclude_filter",
                        optLabel(KindField.EXCLUDE_TAG, excludeTagOpts.get(excludeTagIdx))),
                isAt(LST_X, CONT_Y + 48, fw, 16, mx, my));

        slotBox(g, cx + fw + 4, cy, fw, 16);
        if (keywordBox != null) {
            keywordBox.setX(cx + fw + 7);
            keywordBox.setY(cy + 4);
            keywordBox.setWidth(fw - 8);
            keywordBox.setVisible(currentTab == 0);
            keywordBox.render(g, mx, my, 0);
            if (keyword.isEmpty() && !keywordBox.isFocused()) {
                g.drawString(font, "关键词...", cx + fw + 7, cy + 4, 0x888888, false);
            }
        } else {
            g.drawString(font, "关键词...", cx + fw + 7, cy + 4, 0x888888, false);
        }

        cy += 22;
        btn(g, cx, cy, LST_W, 14,
                encodableOnly ? "仅显示可编码配方 [开]" : "显示所有配方 [关]",
                isAt(LST_X, CONT_Y + 70, LST_W, 14, mx, my));

        cy += 20;
        g.drawString(font, "模组 " + (modOpts.size() - 1) + " 种  |  机器 " + (machineOpts.size() - 1) + " 种",
                cx, cy, 0x555555, false);
        cy += 11;
        g.drawString(font, "当前筛选: " + filteredIdxs.size() + " 条  /  共 " + candidates.size() + " 条",
                cx, cy, 0x555555, false);
        cy += 11;
        if (!menu.getSampleStack().isEmpty()) {
            g.drawString(font, sampleSummary(menu.getSampleStack()), cx, cy, 0x006600, false);
        } else {
            g.drawString(font, "未设置样本 — 显示全部配方", cx, cy, 0x888888, false);
        }
    }

    private void renderResultsTab(GuiGraphics g, int mx, int my) {
        int listTop = topPos + CONT_Y + 2;
        int start = currentPage * gridPageSize();
        int hovered = hoveredResultIndex(mx, my);
        for (int row = 0; row < GRID_ROWS; row++) {
            for (int col = 0; col < GRID_COLS; col++) {
                int idx = start + row * GRID_COLS + col;
                int sx = leftPos + LST_X + col * GRID_SLOT;
                int sy = listTop + row * GRID_SLOT;
                slotBox(g, sx - 1, sy - 1, 18, 18);
                if (idx >= filteredIdxs.size()) {
                    continue;
                }
                RecipeFinderCandidateView c = candidates.get(filteredIdxs.get(idx));
                boolean sel = selectedKeys.contains(c.identityKey());
                boolean hov = hovered == idx;
                if (sel) {
                    g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0x6648D048);
                } else if (hov) {
                    g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0x4433AAFF);
                } else if (!c.encodable()) {
                    g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0x44AA5555);
                }
                g.renderItem(c.previewStack(), sx, sy);
            }
        }

        // Pagination
        int pgTop = listTop + GRID_ROWS * GRID_SLOT + 6;
        int pgCount = pageCount();
        btn(g, leftPos + LST_X, pgTop, 48, 11, "< 上一页",
                currentPage > 0 && isAt(LST_X, CONT_Y + GRID_ROWS * GRID_SLOT + 6, 48, 11, mx, my));
        String pgText = "第 " + (currentPage + 1) + " / " + pgCount + " 页";
        centeredText(g, pgText, leftPos + W / 2, pgTop + 2, 0x444444);
        btn(g, leftPos + LST_X + LST_W - 48, pgTop, 48, 11, "下一页 >",
                currentPage < pgCount - 1 && isAt(LST_X + LST_W - 48, CONT_Y + GRID_ROWS * GRID_SLOT + 6, 48, 11, mx, my));
    }

    /** Rendered in render() so it floats above everything else. */
    private void renderDropdown(GuiGraphics g, int mx, int my) {
        if (openDropdown == null) return;
        List<String> opts = getOpts(openDropdown);
        int visible = Math.min(DD_MAX_ROWS, opts.size());
        if (visible == 0) { openDropdown = null; return; }
        int ddH = visible * DD_ROW_H + 2;

        // Background
        g.fill(ddAbsX, ddAbsY, ddAbsX + ddAbsW, ddAbsY + ddH, 0xFF2D2D2D);
        // Border
        g.fill(ddAbsX - 1, ddAbsY - 1, ddAbsX + ddAbsW + 1, ddAbsY,         0xFF888888);
        g.fill(ddAbsX - 1, ddAbsY - 1, ddAbsX,               ddAbsY + ddH + 1, 0xFF888888);
        g.fill(ddAbsX + ddAbsW, ddAbsY - 1, ddAbsX + ddAbsW + 1, ddAbsY + ddH + 1, 0xFF888888);
        g.fill(ddAbsX - 1, ddAbsY + ddH,    ddAbsX + ddAbsW + 1, ddAbsY + ddH + 1, 0xFF888888);

        int selIdx = getIdx(openDropdown);
        for (int i = 0; i < visible; i++) {
            int optIdx = ddScrollTop + i;
            if (optIdx >= opts.size()) break;
            String opt = opts.get(optIdx);
            int iy = ddAbsY + 1 + i * DD_ROW_H;
            boolean hov = mx >= ddAbsX && mx < ddAbsX + ddAbsW && my >= iy && my < iy + DD_ROW_H;
            boolean isSel = optIdx == selIdx;
            if (isSel)      g.fill(ddAbsX, iy, ddAbsX + ddAbsW, iy + DD_ROW_H, 0xFF3A6A3A);
            else if (hov)   g.fill(ddAbsX, iy, ddAbsX + ddAbsW, iy + DD_ROW_H, 0xFF404040);
            String label = optLabel(openDropdown, opt).getString();
            g.drawString(font, ellipsize(label, (ddAbsW - 8) / 6), ddAbsX + 4, iy + 2, 0xFFFFFF, false);
        }

        // Scrollbar
        if (opts.size() > DD_MAX_ROWS) {
            int total  = opts.size();
            int barH   = Math.max(4, ddH * visible / total);
            int barTop = ddAbsY + 1 + ddH * ddScrollTop / total;
            g.fill(ddAbsX + ddAbsW - 4, ddAbsY + 1,  ddAbsX + ddAbsW - 3, ddAbsY + ddH - 1, 0xFF555555);
            g.fill(ddAbsX + ddAbsW - 4, barTop,       ddAbsX + ddAbsW - 3, barTop + barH,    0xFFAAAAAA);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0x333333, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0x404040, false);
        g.drawString(font, Component.literal(statusLine()), 8, STAT_Y, 0x666666, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (keywordBox != null) {
            keywordBox.setVisible(currentTab == 0);
        }
        super.render(g, mx, my, pt);
        renderHoverTooltip(g, mx, my);
        renderTooltip(g, mx, my);
        // Dropdown must be last (on top)
        renderDropdown(g, mx, my);
    }

    // -------------------------------------------------------------------------
    // Interaction
    // -------------------------------------------------------------------------
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Dropdown click handling first
        if (openDropdown != null) {
            List<String> opts = getOpts(openDropdown);
            int visible = Math.min(DD_MAX_ROWS, opts.size());
            int ddH = visible * DD_ROW_H + 2;
            if (mx >= ddAbsX && mx < ddAbsX + ddAbsW && my >= ddAbsY && my < ddAbsY + ddH) {
                int row = (int) ((my - ddAbsY - 1) / DD_ROW_H);
                int optIdx = ddScrollTop + row;
                if (optIdx >= 0 && optIdx < opts.size()) {
                    setIdx(openDropdown, optIdx);
                }
                openDropdown = null;
                return true;
            }
            openDropdown = null;
            // fall through so the click is also processed normally
        }

        // Header buttons
        if (isAt(30,  HDR_Y, 51, HDR_H, mx, my)) { applyRebuild(); return true; }
        if (isAt(83,  HDR_Y, 51, HDR_H, mx, my)) { encodeSelected(); return true; }
        if (isAt(136, HDR_Y, 51, HDR_H, mx, my)) { selectedKeys.clear(); return true; }
        if (isAt(189, HDR_Y, 49, HDR_H, mx, my)) { selectVisible(); return true; }

        // Tab clicks
        if (isAt(8,   TAB_Y, 115, TAB_H, mx, my)) { currentTab = 0; return true; }
        if (isAt(125, TAB_Y, 115, TAB_H, mx, my)) { currentTab = 1; return true; }

        // Filter tab
        if (currentTab == 0) {
            int fw = fw();
            if (isAt(LST_X,           CONT_Y + 4,  fw, 16, mx, my)) { openDd(KindField.MOD,      LST_X,           CONT_Y + 21, fw); return true; }
            if (isAt(LST_X + fw + 4,  CONT_Y + 4,  fw, 16, mx, my)) { openDd(KindField.MACHINE,  LST_X + fw + 4,  CONT_Y + 21, fw); return true; }
            if (isAt(LST_X,           CONT_Y + 26, fw, 16, mx, my)) { openDd(KindField.MATERIAL, LST_X,           CONT_Y + 43, fw); return true; }
            if (isAt(LST_X + fw + 4,  CONT_Y + 26, fw, 16, mx, my)) { openDd(KindField.OUTPUT,   LST_X + fw + 4,  CONT_Y + 43, fw); return true; }
            if (isAt(LST_X, CONT_Y + 48, fw, 16, mx, my)) { openDd(KindField.EXCLUDE_TAG, LST_X, CONT_Y + 65, fw); return true; }
            if (isAt(LST_X + fw + 4, CONT_Y + 48, fw, 16, mx, my)) {
                if (keywordBox != null) {
                    setFocused(keywordBox);
                    keywordBox.setFocused(true);
                    keywordBox.mouseClicked(mx, my, btn);
                }
                return true;
            }
            if (isAt(LST_X, CONT_Y + 70, LST_W, 14, mx, my)) {
                encodableOnly = !encodableOnly;
                currentPage = 0;
                rebuildFiltered();
                saveState();
                return true;
            }
        }

        // Results tab
        if (currentTab == 1) {
            int pgY = CONT_Y + GRID_ROWS * GRID_SLOT + 6;
            if (currentPage > 0 && isAt(LST_X, pgY, 48, 11, mx, my)) { currentPage--; return true; }
            if (currentPage < pageCount() - 1 && isAt(LST_X + LST_W - 48, pgY, 48, 11, mx, my)) { currentPage++; return true; }
            int idx = hoveredResultIndex(mx, my);
            if (idx >= 0 && idx < filteredIdxs.size()) {
                RecipeFinderCandidateView c = candidates.get(filteredIdxs.get(idx));
                if (!c.encodable()) return true;
                if (!selectedKeys.add(c.identityKey())) selectedKeys.remove(c.identityKey());
                return true;
            }
        }

        if (keywordBox != null) {
            keywordBox.setFocused(false);
        }
        return super.mouseClicked(mx, my, btn);
    }

    /** Open dropdown for a filter field, positioned just below its filter box. */
    private void openDd(KindField field, int relX, int relY, int relW) {
        openDropdown  = field;
        ddScrollTop   = Math.max(0, getIdx(field) - DD_MAX_ROWS / 2);
        ddAbsX = leftPos + relX;
        ddAbsY = topPos  + relY;
        ddAbsW = relW;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (openDropdown != null) {
            int maxScroll = Math.max(0, getOpts(openDropdown).size() - DD_MAX_ROWS);
            ddScrollTop = sy < 0
                    ? Math.min(maxScroll, ddScrollTop + 1)
                    : Math.max(0, ddScrollTop - 1);
            return true;
        }
        if (currentTab == 1 && isAt(LST_X, CONT_Y + 2, GRID_COLS * GRID_SLOT, GRID_ROWS * GRID_SLOT, mx, my)) {
            if (sy < 0 && currentPage < pageCount() - 1) { currentPage++; return true; }
            if (sy > 0 && currentPage > 0)               { currentPage--; return true; }
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (keywordBox != null && keywordBox.isFocused()) {
            if (keywordBox.keyPressed(key, scan, mods)) {
                return true;
            }
        }
        if (key == 256) {  // Escape
            if (openDropdown != null) {
                openDropdown = null;
                return true;
            }
            if (keywordBox != null && keywordBox.isFocused()) {
                keywordBox.setFocused(false);
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }
    
    @Override
    public boolean charTyped(char ch, int mods) {
        if (keywordBox != null && keywordBox.isFocused() && keywordBox.charTyped(ch, mods)) {
            return true;
        }
        return super.charTyped(ch, mods);
    }

    private void selectVisible() {
        int start = currentPage * gridPageSize();
        for (int i = start; i < Math.min(start + gridPageSize(), filteredIdxs.size()); i++) {
            RecipeFinderCandidateView c = candidates.get(filteredIdxs.get(i));
            if (c.encodable()) selectedKeys.add(c.identityKey());
        }
    }

    private void encodeSelected() {
        List<EncodePatternPacket> packets = candidates.stream()
                .filter(c -> selectedKeys.contains(c.identityKey()) && c.encodable())
                .map(RecipeFinderCandidateView::encodePacket)
                .filter(Objects::nonNull)
                .toList();
        if (packets.isEmpty()) return;
        int origSize = packets.size();
        List<EncodePatternPacket> capped = RemoteEncodeRules.capPacketsToServerBulkLimit(packets);
        if (capped.size() < origSize) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.ae2utility.bulk_encode_truncated_client_notice", origSize, capped.size())
                        .withStyle(ChatFormatting.GOLD), false);
            }
        }
        int bulkSid = BulkEncodeSessions.next();
        List<EncodePatternPacket> tagged = capped.stream().map(p -> p.withBulkEncodeSessionId(bulkSid)).toList();
        JeiEncodeQueueDebugLog.info("RecipeFinderScreen.encodeSelected sending {} packets", tagged.size());
        PacketDistributor.sendToServer(new RecipeFinderEncodePacket(tagged));
        selectedKeys.clear();
    }

    private void applyRebuild() {
        RecipeFinderClientState.Snapshot snap = RecipeFinderClientState.rebuild();
        lastSnapVersion = snap.version();
        lastSample      = menu.getSampleStack().copy();
        allCandidates   = List.copyOf(snap.recipes());
        statusMessage   = snap.statusMessage();
        rebuildCandidatesAndOptions();
    }

    // -------------------------------------------------------------------------
    // Hover tooltips
    // -------------------------------------------------------------------------
    private void renderHoverTooltip(GuiGraphics g, int mx, int my) {
        if (menu.getSampleStack().isEmpty() && isAt(LST_X - 1, HDR_Y - 1, 18, 18, mx, my)) {
            g.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.ae2utility.recipe_finder.sample"),
                    Component.translatable("gui.ae2utility.recipe_finder.sample_hint")), mx, my);
            return;
        }
        if (currentTab == 1) {
            int idx = hoveredResultIndex(mx, my);
            if (idx >= 0 && idx < filteredIdxs.size()) {
                RecipeFinderCandidateView c = candidates.get(filteredIdxs.get(idx));
                g.renderTooltip(font, c.previewStack(), mx, my);
                return;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Drawing primitives
    // -------------------------------------------------------------------------
    private void panel(GuiGraphics g, int x, int y, int w, int h, boolean hov) {
        g.fill(x, y, x + w, y + h, hov ? 0xFFD0D0D0 : 0xFFC6C6C6);
        g.fill(x, y, x + w - 1, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + h - 1, 0xFFFFFFFF);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF555555);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF555555);
    }

    private void slotBox(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF8B8B8B);
        g.fill(x, y, x + w - 1, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + h - 1, 0xFF373737);
        g.fill(x + w - 1, y, x + w, y + h, 0xFFFFFFFF);
        g.fill(x, y + h - 1, x + w, y + h, 0xFFFFFFFF);
    }

    private void btn(GuiGraphics g, int x, int y, int w, int h, String label, boolean hov) {
        panel(g, x, y, w, h, hov);
        int textW = font.width(label);
        g.drawString(font, ellipsize(label, w / 6), x + (w - Math.min(textW, (w / 6) * 6)) / 2, y + (h - 8) / 2, 0x333333, false);
    }

    private void filterBox(GuiGraphics g, int x, int y, int w, int h, Component label, boolean hov) {
        slotBox(g, x, y, w, h);
        if (hov) g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x33FFFFFF);
        // Arrow indicator on right side
        g.fill(x + w - 10, y + 4, x + w - 9, y + h - 4, 0xFFAAAAAA);
        g.fill(x + w - 9, y + 5, x + w - 8, y + h - 5, 0xFFAAAAAA);
        g.fill(x + w - 8, y + 6, x + w - 7, y + h - 6, 0xFFAAAAAA);
        String text = ellipsize(label.getString(), (w - 14) / 6);
        g.drawString(font, text, x + 3, y + (h - 8) / 2, 0xFFFFFF, true);
    }

    private void tab(GuiGraphics g, int x, int y, int w, int h, String label, boolean active, boolean hov) {
        int bg = active ? 0xFFC6C6C6 : (hov ? 0xFFBBBBBB : 0xFFB0B0B0);
        g.fill(x, y, x + w, y + h, bg);
        if (active) {
            g.fill(x, y, x + w, y + 1, 0xFFFFFFFF);
            g.fill(x, y, x + 1, y + h, 0xFFFFFFFF);
            g.fill(x + w - 1, y, x + w, y + h, 0xFF888888);
        } else {
            g.fill(x, y, x + w, y + 1, 0xFFAAAAAA);
            g.fill(x, y, x + 1, y + h, 0xFFAAAAAA);
            g.fill(x + w - 1, y, x + w, y + h, 0xFF888888);
            g.fill(x, y + h - 1, x + w, y + h, 0xFF888888);
        }
        int textW = font.width(label);
        g.drawString(font, label, x + (w - textW) / 2, y + (h - 8) / 2, active ? 0x222222 : 0x555555, false);
    }

    private void centeredText(GuiGraphics g, String text, int cx, int y, int color) {
        g.drawString(font, text, cx - font.width(text) / 2, y, color, false);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private Component optLabel(KindField field, String value) {
        if ("all".equals(value)) return Component.translatable("gui.ae2utility.recipe_finder.kind.all");
        return switch (field) {
            case MOD      -> Component.literal(modLabels.getOrDefault(value, value));
            case MACHINE  -> Component.literal(machineLabels.getOrDefault(value, value));
            case MATERIAL, OUTPUT, EXCLUDE_TAG -> Component.literal(featureLabel(value));
        };
    }

    private String featureLabel(String key) {
        String tk = "gui.ae2utility.recipe_finder.feature." + key;
        String resolved = Component.translatable(tk).getString();
        return tk.equals(resolved) ? RecipeFinderFeatureClassifier.featureLabel(key) : resolved;
    }

    private String featureSummary(Set<String> features) {
        return features.stream().map(this::featureLabel).limit(5)
                .reduce((a, b) -> a + ", " + b)
                .orElse(Component.translatable("gui.ae2utility.recipe_finder.kind.all").getString());
    }

    private String sampleSummary(ItemStack sample) {
        String modId = BuiltInRegistries.ITEM.getKey(sample.getItem()).getNamespace();
        String modName = RecipeFinderFeatureClassifier.modDisplayName(modId);
        Set<String> features = RecipeFinderFeatureClassifier.classifyItemStack(sample)
                .stream().filter(f -> !"other".equals(f)).collect(Collectors.toSet());
        if (features.isEmpty()) {
            return "样本模组: " + modName;
        }
        String featText = features.stream().map(this::featureLabel).collect(Collectors.joining(", "));
        return "样本: " + modName + " / " + featText;
    }

    private String statusLine() {
        if (statusMessage != null && !statusMessage.isBlank()) return statusMessage;
        return Component.translatable("gui.ae2utility.recipe_finder.results_ready",
                filteredIdxs.size()).getString();
    }

    private String ellipsize(String s, int max) {
        if (max <= 0) return "";
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 2)) + "..";
    }

    private boolean isAt(int rx, int ry, int w, int h, double mx, double my) {
        return mx >= leftPos + rx && mx < leftPos + rx + w
                && my >= topPos + ry && my < topPos + ry + h;
    }

    private int hoveredResultIndex(double mx, double my) {
        int relX = (int) mx - (leftPos + LST_X);
        int relY = (int) my - (topPos + CONT_Y + 2);
        if (relX < 0 || relY < 0) {
            return -1;
        }
        int col = relX / GRID_SLOT;
        int row = relY / GRID_SLOT;
        if (col < 0 || col >= GRID_COLS || row < 0 || row >= GRID_ROWS) {
            return -1;
        }
        if (relX % GRID_SLOT >= 18 || relY % GRID_SLOT >= 18) {
            return -1;
        }
        return currentPage * gridPageSize() + row * GRID_COLS + col;
    }

    @Override
    public void removed() {
        saveState();
        super.removed();
    }

    private enum KindField { MOD, MACHINE, MATERIAL, OUTPUT, EXCLUDE_TAG }
}
