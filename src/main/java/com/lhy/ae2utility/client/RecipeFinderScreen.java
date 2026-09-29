package com.lhy.ae2utility.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.lhy.ae2utility.client.recipe_finder.RecipeFinderCandidate;
import com.lhy.ae2utility.client.recipe_finder.RecipeFinderClientState;
import com.lhy.ae2utility.client.recipe_finder.RecipeFinderJeiIndexer;
import com.lhy.ae2utility.menu.RecipeFinderMenu;
import com.lhy.ae2utility.recipe_finder.RecipeFinderFeatureClassifier;

public class RecipeFinderScreen extends AbstractContainerScreen<RecipeFinderMenu> {
    private static final String KEY = "gui.ae2utility.recipe_finder.";

    private static final int W = 250;
    private static final int H = 296;

    private static final int HDR_Y = 19;
    private static final int HDR_H = 16;
    private static final int BTN_W = 51;

    private static final int TAB_Y = 38;
    private static final int TAB_H = 15;

    private static final int CONT_Y = 55;
    private static final int CONT_H = 137;

    private static final int STAT_Y = 194;
    // 背包格从 RecipeFinderMenu 的 y=212 开始
    private static final int INV_LBL_Y = 207;

    private static final int GRID_COLS = 13;
    private static final int GRID_ROWS = 6;
    private static final int GRID_SLOT = 18;
    private static final int LST_X = 8;
    private static final int LST_W = 234;
    private static final int FW = (LST_W - 4) / 2;

    private static final int DD_ROW_H = 12;
    private static final int DD_MAX_ROWS = 9;

    private int currentTab = 0;
    private int currentPage = 0;

    private final Set<String> selectedKeys = new LinkedHashSet<>();
    private final List<Integer> filteredIdxs = new ArrayList<>();
    private final Map<String, String> modLabels = new HashMap<>();

    private List<RecipeFinderCandidate> allCandidates = List.of();
    private List<RecipeFinderCandidate> candidates = List.of();

    private List<String> modOpts = List.of("all");
    private List<String> machineOpts = List.of("all");
    private List<String> materialOpts = List.of("all");
    private List<String> outputOpts = List.of("all");
    private List<String> excludeTagOpts = List.of("all");
    private int modIdx, machineIdx, materialIdx, outputIdx, excludeTagIdx;

    private boolean encodableOnly;
    private String keyword = "";
    private int lastSnapVersion = -1;
    private boolean optionsBuilt;
    private ItemStack lastSample = ItemStack.EMPTY;
    private Component status = Component.empty();
    private EditBox keywordBox;
    private String savedModValue = "all";
    private String savedMachineValue = "all";
    private String savedMaterialValue = "all";
    private String savedOutputValue = "all";
    private String savedExcludeTagValue = "all";

    private KindField openDropdown = null;
    private int ddScrollTop = 0;
    private int ddAbsX, ddAbsY, ddAbsW;

    public RecipeFinderScreen(RecipeFinderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
        this.inventoryLabelY = INV_LBL_Y;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        restoreState();
        keywordBox = new EditBox(font, leftPos + LST_X + FW + 7, topPos + CONT_Y + 52, FW - 8, 10,
                Component.translatable(KEY + "keyword_hint"));
        keywordBox.setBordered(false);
        keywordBox.setMaxLength(40);
        keywordBox.setValue(keyword);
        keywordBox.setResponder(value -> {
            keyword = value;
            currentPage = 0;
            rebuildFiltered();
            saveState();
        });
    }

    private void restoreState() {
        savedModValue = RecipeFinderClientState.modValue;
        savedMachineValue = RecipeFinderClientState.machineValue;
        savedMaterialValue = RecipeFinderClientState.materialValue;
        savedOutputValue = RecipeFinderClientState.outputValue;
        savedExcludeTagValue = RecipeFinderClientState.excludeTagValue;
        encodableOnly = RecipeFinderClientState.encodableOnly;
        keyword = RecipeFinderClientState.keyword;
    }

    private void saveState() {
        RecipeFinderClientState.modValue = currentValue(modOpts, modIdx);
        RecipeFinderClientState.machineValue = currentValue(machineOpts, machineIdx);
        RecipeFinderClientState.materialValue = currentValue(materialOpts, materialIdx);
        RecipeFinderClientState.outputValue = currentValue(outputOpts, outputIdx);
        RecipeFinderClientState.excludeTagValue = currentValue(excludeTagOpts, excludeTagIdx);
        RecipeFinderClientState.encodableOnly = encodableOnly;
        RecipeFinderClientState.keyword = keyword;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        keywordBox.tick();
        RecipeFinderClientState.Snapshot snap = RecipeFinderClientState.getSnapshot();
        ItemStack sample = menu.getSampleStack();
        boolean snapChanged = snap.version() != lastSnapVersion;
        boolean sampleChanged = !ItemStack.isSameItemSameTags(lastSample, sample);
        if (snapChanged || sampleChanged) {
            applySnapshot(snap);
        } else {
            status = snap.status();
        }
    }

    private void applySnapshot(RecipeFinderClientState.Snapshot snap) {
        lastSnapVersion = snap.version();
        lastSample = menu.getSampleStack().copy();
        allCandidates = snap.recipes();
        status = snap.status();
        rebuildCandidatesAndOptions();
    }

    private void rebuildCandidatesAndOptions() {
        if (optionsBuilt) {
            rememberSelectedValues();
        }
        optionsBuilt = true;
        modLabels.clear();
        candidates = sampleFilter(allCandidates, menu.getSampleStack());
        currentPage = 0;
        modOpts = buildOpts(KindField.MOD);
        machineOpts = buildOpts(KindField.MACHINE);
        materialOpts = buildOpts(KindField.MATERIAL);
        outputOpts = buildOpts(KindField.OUTPUT);
        excludeTagOpts = buildOpts(KindField.EXCLUDE_TAG);
        restoreSelectedValues();
        Set<String> keys = candidates.stream().map(RecipeFinderCandidate::identityKey).collect(Collectors.toSet());
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
        modIdx = Math.max(0, modOpts.indexOf(savedModValue));
        machineIdx = Math.max(0, machineOpts.indexOf(savedMachineValue));
        materialIdx = Math.max(0, materialOpts.indexOf(savedMaterialValue));
        outputIdx = Math.max(0, outputOpts.indexOf(savedOutputValue));
        excludeTagIdx = Math.max(0, excludeTagOpts.indexOf(savedExcludeTagValue));
    }

    private static String currentValue(List<String> options, int index) {
        if (options.isEmpty()) {
            return "all";
        }
        return options.get(Mth.clamp(index, 0, options.size() - 1));
    }

    /**
     * 样本筛选：配方直接用到/产出样本物品时保留；否则要求配方涉及样本所属模组，
     * 且样本有特征时至少有一个特征交集。
     */
    private static List<RecipeFinderCandidate> sampleFilter(List<RecipeFinderCandidate> src, ItemStack sample) {
        if (sample.isEmpty()) {
            return src;
        }
        String sampleId = BuiltInRegistries.ITEM.getKey(sample.getItem()).toString();
        String sampleModId = BuiltInRegistries.ITEM.getKey(sample.getItem()).getNamespace();
        Set<String> features = sampleFeatures(sample);

        return src.stream().filter(c -> {
            if (c.outputItemIds().contains(sampleId) || c.inputItemIds().contains(sampleId)) {
                return true;
            }
            if (!c.involvedModIds().contains(sampleModId) && !c.sourceModId().equals(sampleModId)) {
                return false;
            }
            return features.isEmpty()
                    || !Collections.disjoint(c.outputFeatureKeys(), features)
                    || !Collections.disjoint(c.inputFeatureKeys(), features);
        }).toList();
    }

    private static Set<String> sampleFeatures(ItemStack sample) {
        return RecipeFinderFeatureClassifier.classifyItemStack(sample).stream()
                .filter(f -> !"other".equals(f))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> buildOpts(KindField field) {
        LinkedHashSet<String> vals = new LinkedHashSet<>();
        vals.add("all");
        for (RecipeFinderCandidate c : candidates) {
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
        }
        return List.copyOf(vals);
    }

    private void setIdx(KindField field, int idx) {
        switch (field) {
            case MOD -> modIdx = idx;
            case MACHINE -> machineIdx = idx;
            case MATERIAL -> materialIdx = idx;
            case OUTPUT -> outputIdx = idx;
            case EXCLUDE_TAG -> excludeTagIdx = idx;
        }
        currentPage = 0;
        rebuildFiltered();
        saveState();
    }

    private int getIdx(KindField field) {
        return switch (field) {
            case MOD -> modIdx;
            case MACHINE -> machineIdx;
            case MATERIAL -> materialIdx;
            case OUTPUT -> outputIdx;
            case EXCLUDE_TAG -> excludeTagIdx;
        };
    }

    private List<String> getOpts(KindField field) {
        return switch (field) {
            case MOD -> modOpts;
            case MACHINE -> machineOpts;
            case MATERIAL -> materialOpts;
            case OUTPUT -> outputOpts;
            case EXCLUDE_TAG -> excludeTagOpts;
        };
    }

    private void rebuildFiltered() {
        filteredIdxs.clear();
        String mf = currentValue(modOpts, modIdx);
        String mcf = currentValue(machineOpts, machineIdx);
        String matf = currentValue(materialOpts, materialIdx);
        String outf = currentValue(outputOpts, outputIdx);
        String exf = currentValue(excludeTagOpts, excludeTagIdx);
        String kw = keyword.toLowerCase();
        for (int i = 0; i < candidates.size(); i++) {
            RecipeFinderCandidate c = candidates.get(i);
            if (!"all".equals(mf) && !mf.equals(c.sourceModId())) continue;
            if (!"all".equals(mcf) && !mcf.equals(c.machineLabel())) continue;
            if (!"all".equals(matf) && !c.inputFeatureKeys().contains(matf)) continue;
            if (!"all".equals(outf) && !c.outputFeatureKeys().contains(outf)) continue;
            if (!"all".equals(exf) && (c.inputFeatureKeys().contains(exf) || c.outputFeatureKeys().contains(exf))) continue;
            if (!kw.isEmpty() && !c.displayName().toLowerCase().contains(kw)) continue;
            if (encodableOnly && !c.encodable()) continue;
            filteredIdxs.add(i);
        }
        currentPage = Mth.clamp(currentPage, 0, pageCount() - 1);
    }

    private int pageCount() {
        return Math.max(1, (filteredIdxs.size() + gridPageSize() - 1) / gridPageSize());
    }

    private static int gridPageSize() {
        return GRID_COLS * GRID_ROWS;
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;

        panel(g, x, y, W, H, false);
        slotBox(g, x + LST_X - 1, y + HDR_Y - 1, 18, 18);

        btn(g, x + 30, y + HDR_Y, BTN_W, HDR_H, tr("refresh"), isAt(30, HDR_Y, BTN_W, HDR_H, mx, my));
        btn(g, x + 83, y + HDR_Y, BTN_W, HDR_H, tr("encode_selected"), isAt(83, HDR_Y, BTN_W, HDR_H, mx, my));
        btn(g, x + 136, y + HDR_Y, BTN_W, HDR_H, tr("clear_selection"), isAt(136, HDR_Y, BTN_W, HDR_H, mx, my));
        btn(g, x + 189, y + HDR_Y, BTN_W - 2, HDR_H, tr("select_visible"), isAt(189, HDR_Y, BTN_W - 2, HDR_H, mx, my));

        g.fill(x + 7, y + 37, x + W - 7, y + 38, 0xFF999999);

        tab(g, x + 8, y + TAB_Y, 115, TAB_H, tr("tab_filter"),
                currentTab == 0, isAt(8, TAB_Y, 115, TAB_H, mx, my));
        tab(g, x + 125, y + TAB_Y, 115, TAB_H, tr("tab_results", filteredIdxs.size()),
                currentTab == 1, isAt(125, TAB_Y, 115, TAB_H, mx, my));

        g.fill(x + 7, y + CONT_Y - 1, x + W - 7, y + CONT_Y, 0xFF999999);

        if (currentTab == 0) {
            renderFilterTab(g, mx, my);
        } else {
            renderResultsTab(g, mx, my);
        }

        g.fill(x + 7, y + CONT_Y + CONT_H + 1, x + W - 7, y + CONT_Y + CONT_H + 2, 0xFF999999);
        g.fill(x + 7, y + INV_LBL_Y - 4, x + W - 7, y + INV_LBL_Y - 3, 0xFF999999);

        for (Slot slot : menu.slots) {
            if (slot.index != RecipeFinderMenu.SAMPLE_SLOT_INDEX) {
                slotBox(g, x + slot.x - 1, y + slot.y - 1, 18, 18);
            }
        }
    }

    private void renderFilterTab(GuiGraphics g, int mx, int my) {
        int cx = leftPos + LST_X;
        int cy = topPos + CONT_Y + 4;

        filterBox(g, cx, cy, FW, 16, tr("mod_filter", optLabel(KindField.MOD)),
                isAt(LST_X, CONT_Y + 4, FW, 16, mx, my));
        filterBox(g, cx + FW + 4, cy, FW, 16, tr("machine_filter", optLabel(KindField.MACHINE)),
                isAt(LST_X + FW + 4, CONT_Y + 4, FW, 16, mx, my));

        cy += 22;
        filterBox(g, cx, cy, FW, 16, tr("material_filter", optLabel(KindField.MATERIAL)),
                isAt(LST_X, CONT_Y + 26, FW, 16, mx, my));
        filterBox(g, cx + FW + 4, cy, FW, 16, tr("output_filter", optLabel(KindField.OUTPUT)),
                isAt(LST_X + FW + 4, CONT_Y + 26, FW, 16, mx, my));

        cy += 22;
        filterBox(g, cx, cy, FW, 16, tr("exclude_filter", optLabel(KindField.EXCLUDE_TAG)),
                isAt(LST_X, CONT_Y + 48, FW, 16, mx, my));

        slotBox(g, cx + FW + 4, cy, FW, 16);
        keywordBox.render(g, mx, my, 0);
        if (keyword.isEmpty() && !keywordBox.isFocused()) {
            g.drawString(font, tr("keyword_hint"), cx + FW + 7, cy + 4, 0x888888, false);
        }

        cy += 22;
        btn(g, cx, cy, LST_W, 14, tr(encodableOnly ? "only_encodable_on" : "only_encodable_off"),
                isAt(LST_X, CONT_Y + 70, LST_W, 14, mx, my));

        cy += 20;
        g.drawString(font, tr("option_counts", modOpts.size() - 1, machineOpts.size() - 1), cx, cy, 0x555555, false);
        cy += 11;
        g.drawString(font, tr("filter_counts", filteredIdxs.size(), candidates.size()), cx, cy, 0x555555, false);
        cy += 11;
        ItemStack sample = menu.getSampleStack();
        if (sample.isEmpty()) {
            g.drawString(font, tr("no_sample"), cx, cy, 0x888888, false);
        } else {
            g.drawString(font, ellipsize(sampleSummary(sample).getString(), LST_W), cx, cy, 0x006600, false);
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
                RecipeFinderCandidate c = candidates.get(filteredIdxs.get(idx));
                if (selectedKeys.contains(c.identityKey())) {
                    g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0x6648D048);
                } else if (hovered == idx) {
                    g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0x4433AAFF);
                } else if (!c.encodable()) {
                    g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0x44AA5555);
                }
                g.renderItem(c.previewStack(), sx, sy);
            }
        }

        int pgY = CONT_Y + GRID_ROWS * GRID_SLOT + 6;
        int pgCount = pageCount();
        btn(g, leftPos + LST_X, topPos + pgY, 48, 11, tr("prev_page"),
                currentPage > 0 && isAt(LST_X, pgY, 48, 11, mx, my));
        Component pgText = tr("page", currentPage + 1, pgCount);
        g.drawString(font, pgText, leftPos + W / 2 - font.width(pgText) / 2, topPos + pgY + 2, 0x444444, false);
        btn(g, leftPos + LST_X + LST_W - 48, topPos + pgY, 48, 11, tr("next_page"),
                currentPage < pgCount - 1 && isAt(LST_X + LST_W - 48, pgY, 48, 11, mx, my));
    }

    private void renderDropdown(GuiGraphics g, int mx, int my) {
        if (openDropdown == null) {
            return;
        }
        List<String> opts = getOpts(openDropdown);
        int visible = Math.min(DD_MAX_ROWS, opts.size());
        if (visible == 0) {
            openDropdown = null;
            return;
        }
        int ddH = visible * DD_ROW_H + 2;

        g.pose().pushPose();
        // 高于物品渲染层，避免背包物品盖住下拉框
        g.pose().translate(0, 0, 300);
        g.fill(ddAbsX, ddAbsY, ddAbsX + ddAbsW, ddAbsY + ddH, 0xFF2D2D2D);
        g.fill(ddAbsX - 1, ddAbsY - 1, ddAbsX + ddAbsW + 1, ddAbsY, 0xFF888888);
        g.fill(ddAbsX - 1, ddAbsY - 1, ddAbsX, ddAbsY + ddH + 1, 0xFF888888);
        g.fill(ddAbsX + ddAbsW, ddAbsY - 1, ddAbsX + ddAbsW + 1, ddAbsY + ddH + 1, 0xFF888888);
        g.fill(ddAbsX - 1, ddAbsY + ddH, ddAbsX + ddAbsW + 1, ddAbsY + ddH + 1, 0xFF888888);

        int selIdx = getIdx(openDropdown);
        for (int i = 0; i < visible; i++) {
            int optIdx = ddScrollTop + i;
            if (optIdx >= opts.size()) {
                break;
            }
            int iy = ddAbsY + 1 + i * DD_ROW_H;
            boolean hov = mx >= ddAbsX && mx < ddAbsX + ddAbsW && my >= iy && my < iy + DD_ROW_H;
            if (optIdx == selIdx) {
                g.fill(ddAbsX, iy, ddAbsX + ddAbsW, iy + DD_ROW_H, 0xFF3A6A3A);
            } else if (hov) {
                g.fill(ddAbsX, iy, ddAbsX + ddAbsW, iy + DD_ROW_H, 0xFF404040);
            }
            String label = optLabel(openDropdown, opts.get(optIdx));
            g.drawString(font, ellipsize(label, ddAbsW - 8), ddAbsX + 4, iy + 2, 0xFFFFFF, false);
        }

        if (opts.size() > DD_MAX_ROWS) {
            int total = opts.size();
            int barH = Math.max(4, ddH * visible / total);
            int barTop = ddAbsY + 1 + ddH * ddScrollTop / total;
            g.fill(ddAbsX + ddAbsW - 4, ddAbsY + 1, ddAbsX + ddAbsW - 3, ddAbsY + ddH - 1, 0xFF555555);
            g.fill(ddAbsX + ddAbsW - 4, barTop, ddAbsX + ddAbsW - 3, barTop + barH, 0xFFAAAAAA);
        }
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0x333333, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0x404040, false);
        Component line = status.getString().isBlank() ? tr("results_ready", filteredIdxs.size()) : status;
        g.drawString(font, line, 8, STAT_Y, 0x666666, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        keywordBox.setVisible(currentTab == 0);
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderHoverTooltip(g, mx, my);
        renderTooltip(g, mx, my);
        renderDropdown(g, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (openDropdown != null) {
            List<String> opts = getOpts(openDropdown);
            int ddH = Math.min(DD_MAX_ROWS, opts.size()) * DD_ROW_H + 2;
            if (mx >= ddAbsX && mx < ddAbsX + ddAbsW && my >= ddAbsY && my < ddAbsY + ddH) {
                int optIdx = ddScrollTop + (int) ((my - ddAbsY - 1) / DD_ROW_H);
                if (optIdx >= 0 && optIdx < opts.size()) {
                    setIdx(openDropdown, optIdx);
                }
                openDropdown = null;
                return true;
            }
            openDropdown = null;
        }

        if (isAt(30, HDR_Y, BTN_W, HDR_H, mx, my)) {
            applySnapshot(RecipeFinderClientState.rebuild());
            return true;
        }
        if (isAt(83, HDR_Y, BTN_W, HDR_H, mx, my)) {
            encodeSelected();
            return true;
        }
        if (isAt(136, HDR_Y, BTN_W, HDR_H, mx, my)) {
            selectedKeys.clear();
            return true;
        }
        if (isAt(189, HDR_Y, BTN_W - 2, HDR_H, mx, my)) {
            selectVisible();
            return true;
        }

        if (isAt(8, TAB_Y, 115, TAB_H, mx, my) || isAt(125, TAB_Y, 115, TAB_H, mx, my)) {
            currentTab = isAt(8, TAB_Y, 115, TAB_H, mx, my) ? 0 : 1;
            unfocusKeyword();
            return true;
        }

        if (currentTab == 0) {
            if (isAt(LST_X, CONT_Y + 4, FW, 16, mx, my)) {
                openDd(KindField.MOD, LST_X, CONT_Y + 21);
                return true;
            }
            if (isAt(LST_X + FW + 4, CONT_Y + 4, FW, 16, mx, my)) {
                openDd(KindField.MACHINE, LST_X + FW + 4, CONT_Y + 21);
                return true;
            }
            if (isAt(LST_X, CONT_Y + 26, FW, 16, mx, my)) {
                openDd(KindField.MATERIAL, LST_X, CONT_Y + 43);
                return true;
            }
            if (isAt(LST_X + FW + 4, CONT_Y + 26, FW, 16, mx, my)) {
                openDd(KindField.OUTPUT, LST_X + FW + 4, CONT_Y + 43);
                return true;
            }
            if (isAt(LST_X, CONT_Y + 48, FW, 16, mx, my)) {
                openDd(KindField.EXCLUDE_TAG, LST_X, CONT_Y + 65);
                return true;
            }
            if (isAt(LST_X + FW + 4, CONT_Y + 48, FW, 16, mx, my)) {
                setFocused(keywordBox);
                keywordBox.mouseClicked(mx, my, button);
                return true;
            }
            if (isAt(LST_X, CONT_Y + 70, LST_W, 14, mx, my)) {
                encodableOnly = !encodableOnly;
                currentPage = 0;
                rebuildFiltered();
                saveState();
                return true;
            }
        } else {
            int pgY = CONT_Y + GRID_ROWS * GRID_SLOT + 6;
            if (currentPage > 0 && isAt(LST_X, pgY, 48, 11, mx, my)) {
                currentPage--;
                return true;
            }
            if (currentPage < pageCount() - 1 && isAt(LST_X + LST_W - 48, pgY, 48, 11, mx, my)) {
                currentPage++;
                return true;
            }
            int idx = hoveredResultIndex(mx, my);
            if (idx >= 0 && idx < filteredIdxs.size()) {
                RecipeFinderCandidate c = candidates.get(filteredIdxs.get(idx));
                if (c.encodable() && !selectedKeys.add(c.identityKey())) {
                    selectedKeys.remove(c.identityKey());
                }
                return true;
            }
        }

        unfocusKeyword();
        return super.mouseClicked(mx, my, button);
    }

    private void unfocusKeyword() {
        if (getFocused() == keywordBox) {
            setFocused(null);
        }
        keywordBox.setFocused(false);
    }

    private void openDd(KindField field, int relX, int relY) {
        openDropdown = field;
        ddScrollTop = Mth.clamp(getIdx(field) - DD_MAX_ROWS / 2, 0,
                Math.max(0, getOpts(field).size() - DD_MAX_ROWS));
        ddAbsX = leftPos + relX;
        ddAbsY = topPos + relY;
        ddAbsW = FW;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (openDropdown != null) {
            int maxScroll = Math.max(0, getOpts(openDropdown).size() - DD_MAX_ROWS);
            ddScrollTop = delta < 0 ? Math.min(maxScroll, ddScrollTop + 1) : Math.max(0, ddScrollTop - 1);
            return true;
        }
        if (currentTab == 1 && isAt(LST_X, CONT_Y + 2, GRID_COLS * GRID_SLOT, GRID_ROWS * GRID_SLOT, mx, my)) {
            if (delta < 0 && currentPage < pageCount() - 1) {
                currentPage++;
                return true;
            }
            if (delta > 0 && currentPage > 0) {
                currentPage--;
                return true;
            }
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256 && openDropdown != null) {
            openDropdown = null;
            return true;
        }
        if (keywordBox.isFocused()) {
            if (key == 256) {
                unfocusKeyword();
                return true;
            }
            // 输入框聚焦时吞掉按键，避免 E 等快捷键关闭界面
            if (keywordBox.keyPressed(key, scan, mods) || keywordBox.canConsumeInput()) {
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        if (keywordBox.isFocused() && keywordBox.charTyped(ch, mods)) {
            return true;
        }
        return super.charTyped(ch, mods);
    }

    private void selectVisible() {
        int start = currentPage * gridPageSize();
        for (int i = start; i < Math.min(start + gridPageSize(), filteredIdxs.size()); i++) {
            RecipeFinderCandidate c = candidates.get(filteredIdxs.get(i));
            if (c.encodable()) {
                selectedKeys.add(c.identityKey());
            }
        }
    }

    private void encodeSelected() {
        List<RecipeFinderCandidate> selected = candidates.stream()
                .filter(c -> c.encodable() && selectedKeys.contains(c.identityKey()))
                .toList();
        if (selected.isEmpty()) {
            return;
        }
        RecipeFinderJeiIndexer.encode(selected, hasShiftDown());
        selectedKeys.clear();
    }

    private void renderHoverTooltip(GuiGraphics g, int mx, int my) {
        if (menu.getSampleStack().isEmpty() && isAt(LST_X - 1, HDR_Y - 1, 18, 18, mx, my)) {
            g.renderComponentTooltip(font, List.of(tr("sample"), tr("sample_hint")), mx, my);
            return;
        }
        if (isAt(83, HDR_Y, BTN_W, HDR_H, mx, my)) {
            g.renderComponentTooltip(font, List.of(tr("encode_selected"),
                    Component.translatable("jei.tooltip.ae2utility.batch_encode_shift_hint")
                            .withStyle(ChatFormatting.GRAY)), mx, my);
            return;
        }
        if (currentTab == 1 && openDropdown == null) {
            int idx = hoveredResultIndex(mx, my);
            if (idx >= 0 && idx < filteredIdxs.size()) {
                RecipeFinderCandidate c = candidates.get(filteredIdxs.get(idx));
                List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, c.previewStack()));
                lines.add(tr("machine_filter", c.machineLabel()).withStyle(ChatFormatting.GRAY));
                lines.add(tr("mod_filter", c.sourceModName()).withStyle(ChatFormatting.GRAY));
                if (!c.encodable()) {
                    lines.add(tr("encodable_no").withStyle(ChatFormatting.RED));
                }
                g.renderComponentTooltip(font, lines, mx, my);
            }
        }
    }

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

    private void btn(GuiGraphics g, int x, int y, int w, int h, Component label, boolean hov) {
        panel(g, x, y, w, h, hov);
        String text = ellipsize(label.getString(), w - 4);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, 0x333333, false);
    }

    private void filterBox(GuiGraphics g, int x, int y, int w, int h, Component label, boolean hov) {
        slotBox(g, x, y, w, h);
        if (hov) {
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x33FFFFFF);
        }
        g.fill(x + w - 10, y + 4, x + w - 9, y + h - 4, 0xFFAAAAAA);
        g.fill(x + w - 9, y + 5, x + w - 8, y + h - 5, 0xFFAAAAAA);
        g.fill(x + w - 8, y + 6, x + w - 7, y + h - 6, 0xFFAAAAAA);
        g.drawString(font, ellipsize(label.getString(), w - 14), x + 3, y + (h - 8) / 2, 0xFFFFFF, true);
    }

    private void tab(GuiGraphics g, int x, int y, int w, int h, Component label, boolean active, boolean hov) {
        g.fill(x, y, x + w, y + h, active ? 0xFFC6C6C6 : (hov ? 0xFFBBBBBB : 0xFFB0B0B0));
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
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2,
                active ? 0x222222 : 0x555555, false);
    }

    private String optLabel(KindField field) {
        return optLabel(field, currentValue(getOpts(field), getIdx(field)));
    }

    private String optLabel(KindField field, String value) {
        if ("all".equals(value)) {
            return tr("kind.all").getString();
        }
        return switch (field) {
            case MOD -> modLabels.getOrDefault(value, value);
            case MACHINE -> value;
            case MATERIAL, OUTPUT, EXCLUDE_TAG -> featureLabel(value);
        };
    }

    private static String featureLabel(String key) {
        return tr("feature." + key).getString();
    }

    private static Component sampleSummary(ItemStack sample) {
        String modName = RecipeFinderFeatureClassifier.modDisplayName(
                BuiltInRegistries.ITEM.getKey(sample.getItem()).getNamespace());
        Set<String> features = sampleFeatures(sample);
        if (features.isEmpty()) {
            return tr("sample_mod", modName);
        }
        return tr("sample_summary", modName,
                features.stream().map(RecipeFinderScreen::featureLabel).collect(Collectors.joining(", ")));
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable(KEY + key, args);
    }

    private String ellipsize(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }
        return font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width(".."))) + "..";
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
        if (col >= GRID_COLS || row >= GRID_ROWS) {
            return -1;
        }
        return currentPage * gridPageSize() + row * GRID_COLS + col;
    }

    @Override
    public void removed() {
        if (optionsBuilt) {
            saveState();
        }
        super.removed();
    }

    private enum KindField { MOD, MACHINE, MATERIAL, OUTPUT, EXCLUDE_TAG }
}
