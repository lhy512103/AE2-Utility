package com.lhy.ae2utility.client.gui;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import com.lhy.ae2utility.Ae2UtilityMod;

/** 从 {@code assets/<mod>/gui/redstone_signal_card_layout.json} 载入；缺资源时用内嵌默认。 */
public final class RedstoneSignalCardGuiSpecs {

    private static final ResourceLocation RESOURCE =
            ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "gui/redstone_signal_card_layout.json");

    private static volatile RedstoneSignalCardGuiSpecs cached;

    public final ResourceLocation panelTexture;
    public final int texW;
    public final int texH;
    public final int panelBgU;
    public final int panelBgV;
    public final int panelWidth;
    public final int panelHeight;

    public final int knobUvInactiveU;
    public final int knobUvInactiveV;
    public final int knobUvActiveU;
    public final int knobUvActiveV;
    public final int knobW;
    public final int knobH;
    public final int trackHitHalfHeight;

    public final int milestoneStep;
    public final int maxColumnInclusive;
    public final int sliderLeftX;
    public final int[] sliderCenterYFromPanelTop;
    public final int trackLengthPx;

    public final int tickLabelX;
    public final int tickLabelY;
    public final int secondsLabelY;
    public final int minutesLabelY;

    public final int titleX;
    public final int titleY;
    public final String titleLangKey;
    public final int titleTextColorArgb;
    public final int modeYOffsetBelowTitleBaselinePx;
    public final int hintYOffsetBelowTitleBaselinePx;
    public final int hintYOffsetBelowModeBaselinePx;
    public final int hintMaxWidth;

    public final String[] hintLangKeys;
    public final int[] hintTextColorsArgb;

    /**
     * AE2 {@link appeng.client.gui.widgets.IconButton}：16×16 图标左上角相对<strong>面板左上角</strong>
     * （X 常为负数以落在侧边条内）。
     */
    public final int modeCycleOffsetXFromPanelLeft;
    public final int modeCycleOffsetYFromPanelTop;

    /**
     * 侧栏贴图：须为带 {@code .png} 的<strong>普通 GUI 纹理路径</strong>，与 {@link appeng.client.gui.Icon#TEXTURE}
     *（{@code textures/guis/states.png}）同一套绑定方式；若 JSON 仅有 {@code sprite} 且无后缀，解析时会补 {@code .png}。
     */
    public final ResourceLocation sidebarBackgroundTexture;

    /**
     * 侧栏占位宽（九切片横向拉伸）；右边缘对齐 {@code panelLeft + sidebarRightOverlapPanelLeftPx}。
     */
    public final int sidebarOuterWidthPx;
    /** 占位高（纵向拉伸）；与 AE VerticalButtonBar 一样，blitSprite 实际高度会再大 4px。 */
    public final int sidebarOuterHeightPx;
    /** 贴图矩形右边界相对面板左边界：正值表示右缘压住面板左上角左侧若干像素避免缝。默认 0 即 sx+sw == panelLeft。 */
    public final int sidebarRightOverlapPanelLeftPx;
    /** 贴图左上角相对面板左上角垂直偏移（AE 常为 -1）。 */
    public final int sidebarTopOffsetFromPanelTopPx;

    public final int toolbarBgW;
    public final int toolbarBgH;
    public final int toolbarIconPx;

    public final int closeButtonOffsetXFromPanelLeft;
    public final int closeButtonOffsetYFromPanelTop;
    public final int closeButtonW;
    public final int closeButtonH;
    public final int closeIconOffsetX;
    public final int closeIconOffsetY;

    RedstoneSignalCardGuiSpecs(JsonObject root) {
        JsonObject bg = root.getAsJsonObject("panelBackground");
        this.panelTexture = ResourceLocation.parse(bg.get("texture").getAsString());
        this.texW = getInt(bg, "textureWidth", 256);
        this.texH = getInt(bg, "textureHeight", 256);
        this.panelBgU = getInt(bg, "u", 0);
        this.panelBgV = getInt(bg, "v", 0);
        this.panelWidth = getInt(bg, "panelWidth", 219);
        this.panelHeight = getInt(bg, "panelHeight", 88);

        JsonObject kn = root.getAsJsonObject("sliderKnobs");
        this.knobUvInactiveU = getInt(kn, "inactiveUvU", 243);
        this.knobUvInactiveV = getInt(kn, "inactiveUvV", 0);
        this.knobUvActiveU = getInt(kn, "activeUvU", 243);
        this.knobUvActiveV = getInt(kn, "activeUvV", 7);
        this.knobW = getInt(kn, "width", 13);
        this.knobH = getInt(kn, "height", 7);
        int hit = getInt(kn, "trackHitHeight", 14);
        this.trackHitHalfHeight = Math.max(4, hit / 2);

        JsonObject sl = root.getAsJsonObject("sliders");
        this.milestoneStep = getInt(sl, "milestoneStep", 10);
        this.maxColumnInclusive = getInt(sl, "maxColumnInclusive", 60);
        this.sliderLeftX = getInt(sl, "firstTrackCenterXFromPanelLeft", 52);
        int firstCy = getInt(sl, "firstTrackCenterYFromPanelTop", 53);
        int gap = getInt(sl, "rowGapPx", 13);
        this.trackLengthPx = getInt(sl, "trackLengthPx", 153);
        this.sliderCenterYFromPanelTop = new int[] {
                firstCy,
                firstCy + gap,
                firstCy + 2 * gap
        };

        JsonArray tsl = root.getAsJsonArray("tickSecondMinuteLabelsFromPanelLeftTop");
        JsonObject tickBase = tsl.get(0).getAsJsonObject();
        this.tickLabelX = getInt(tickBase, "x", 28);
        this.tickLabelY = getInt(tickBase, "y", 50);
        if (tsl.size() > 1) {
            JsonObject sec = tsl.get(1).getAsJsonObject();
            this.secondsLabelY = this.tickLabelY + getInt(sec, "dyFromTickRow", 5);
        } else {
            this.secondsLabelY = this.tickLabelY + 5;
        }
        if (tsl.size() > 2) {
            JsonObject minRow = tsl.get(2).getAsJsonObject();
            this.minutesLabelY = this.tickLabelY + getInt(minRow, "dyFromTickRow", 10);
        } else {
            this.minutesLabelY = this.tickLabelY + 10;
        }

        JsonObject titleObj = root.getAsJsonObject("title");
        this.titleX = getInt(titleObj, "x", 13);
        this.titleY = getInt(titleObj, "y", 5);
        this.titleLangKey = getString(titleObj, "translationKey", "gui.ae2utility.redstone_signal_card");
        this.titleTextColorArgb = parseRgbArgb(getString(titleObj, "textColorRgb", "#413F54"));
        this.modeYOffsetBelowTitleBaselinePx = getInt(titleObj, "modeYOffsetBelowTitleBaselinePx", 4);
        this.hintYOffsetBelowTitleBaselinePx = getInt(titleObj, "hintYOffsetBelowTitleBaselinePx", 4);
        this.hintYOffsetBelowModeBaselinePx = getInt(titleObj, "hintYOffsetBelowModeBaselinePx", 2);
        this.hintMaxWidth = getInt(titleObj, "hintMaxWidth", 160);

        JsonArray hints = root.getAsJsonArray("modeHints");
        this.hintLangKeys = new String[3];
        this.hintTextColorsArgb = new int[3];
        for (int i = 0; i < 3; i++) {
            if (i < hints.size()) {
                JsonObject ho = hints.get(i).getAsJsonObject();
                this.hintLangKeys[i] = getString(ho, "translationKey", "");
                this.hintTextColorsArgb[i] = parseRgbArgb(getString(ho, "textColorRgb", "#FFFFFF"));
            } else {
                this.hintLangKeys[i] = "";
                this.hintTextColorsArgb[i] = 0xFFFFFFFF;
            }
        }

        if (root.has("modeCycleButton")) {
            JsonObject mc = root.getAsJsonObject("modeCycleButton");
            if (mc.has("offsetXFromPanelLeft")) {
                this.modeCycleOffsetXFromPanelLeft = getInt(mc, "offsetXFromPanelLeft", -19);
            } else {
                this.modeCycleOffsetXFromPanelLeft = getInt(mc, "panelX", -19);
            }
            if (mc.has("offsetYFromPanelTop")) {
                this.modeCycleOffsetYFromPanelTop = getInt(mc, "offsetYFromPanelTop", 4);
            } else {
                this.modeCycleOffsetYFromPanelTop = getInt(mc, "panelY", 4);
            }
        } else {
            JsonArray icons = root.getAsJsonArray("modeAeIcons");
            if (icons != null && !icons.isEmpty()) {
                JsonObject io = icons.get(0).getAsJsonObject();
                this.modeCycleOffsetXFromPanelLeft = getInt(io, "panelX", -19);
                this.modeCycleOffsetYFromPanelTop = getInt(io, "panelY", 4);
            } else {
                this.modeCycleOffsetXFromPanelLeft = -19;
                this.modeCycleOffsetYFromPanelTop = 4;
            }
        }

        JsonObject sb = root.has("aeSidebarStrip") ? root.getAsJsonObject("aeSidebarStrip") : null;
        String sideTex;
        if (sb != null && sb.has("backgroundTexture")) {
            sideTex = getString(sb, "backgroundTexture", "");
        } else {
            sideTex = getString(sb, "sprite", "ae2:textures/gui/sprites/vertical_buttons_bg");
            if (!sideTex.endsWith(".png")) {
                sideTex = sideTex + ".png";
            }
        }
        if (sideTex.isBlank()) {
            sideTex = "ae2:textures/gui/sprites/vertical_buttons_bg.png";
        }
        this.sidebarBackgroundTexture = ResourceLocation.parse(sideTex);
        this.sidebarOuterWidthPx = getInt(sb, "outerWidthPx", 22);
        this.sidebarOuterHeightPx = getInt(sb, "outerHeightPx", 32);
        this.sidebarRightOverlapPanelLeftPx = getInt(sb, "rightOverlapPanelLeftPx", 0);
        this.sidebarTopOffsetFromPanelTopPx = getInt(sb, "topOffsetFromPanelTopPx", -1);

        JsonObject tb = root.getAsJsonObject("toolbarButtonPx");
        this.toolbarBgW = getInt(tb, "width", 18);
        this.toolbarBgH = getInt(tb, "height", 20);
        this.toolbarIconPx = getInt(tb, "innerIconPx", 16);

        JsonObject cl = root.getAsJsonObject("closeButton");
        this.closeButtonOffsetXFromPanelLeft = getInt(cl, "offsetXFromPanelLeft", 214);
        this.closeButtonOffsetYFromPanelTop = getInt(cl, "offsetYFromPanelTop", -5);
        this.closeButtonW = getInt(cl, "width", 25);
        this.closeButtonH = getInt(cl, "height", 22);
        this.closeIconOffsetX = getInt(cl, "iconOffsetX", 1);
        this.closeIconOffsetY = getInt(cl, "iconOffsetY", 0);

    }

    static int parseRgbArgb(String hex) {
        String s = hex.startsWith("#") ? hex.substring(1) : hex;
        if (s.length() == 6) {
            try {
                return 0xFF000000 | Integer.parseUnsignedInt(s, 16);
            } catch (@SuppressWarnings("unused") NumberFormatException ignored) {
                return 0xFFFFFFFF;
            }
        }
        return 0xFFFFFFFF;
    }

    private static int getInt(@Nullable JsonObject j, String k, int def) {
        return j != null && j.has(k) ? j.get(k).getAsInt() : def;
    }

    private static String getString(@Nullable JsonObject j, String k, String def) {
        return j != null && j.has(k) ? j.get(k).getAsString() : def;
    }

    public static RedstoneSignalCardGuiSpecs getOrLoad() {
        if (cached != null) {
            return cached;
        }
        reloadFromDisk();
        return Objects.requireNonNullElseGet(cached, RedstoneSignalCardGuiSpecs::embeddedDefaults);
    }

    public static void reloadFromDisk() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) {
            cached = embeddedDefaults();
            return;
        }
        try {
            var opt = mc.getResourceManager().getResource(RESOURCE);
            if (opt.isEmpty()) {
                cached = embeddedDefaults();
                return;
            }
            try (var reader = new InputStreamReader(opt.get().open(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                cached = new RedstoneSignalCardGuiSpecs(root);
            }
        } catch (Exception e) {
            Ae2UtilityMod.LOGGER.warn("Failed to load {}, using defaults: {}", RESOURCE, e.toString());
            cached = embeddedDefaults();
        }
    }

    private static RedstoneSignalCardGuiSpecs embeddedDefaults() {
                JsonObject root = JsonParser.parseString("""
                {"panelBackground":{"texture":"ae2utility:textures/gui/redstone_signal_card_gui.png","textureWidth":256,"textureHeight":256,"u":0,"v":0,"panelWidth":219,"panelHeight":88},"sliderKnobs":{"inactiveUvU":243,"inactiveUvV":0,"activeUvU":243,"activeUvV":7,"width":13,"height":7,"trackHitHeight":14},"sliders":{"milestoneStep":10,"maxColumnInclusive":60,"firstTrackCenterXFromPanelLeft":52,"firstTrackCenterYFromPanelTop":53,"trackLengthPx":153,"rowGapPx":12},"tickSecondMinuteLabelsFromPanelLeftTop":[{"x":13,"y":50,"unit":"ticks"},{"dxFromTickRow":0,"dyFromTickRow":5},{"dxFromTickRow":0,"dyFromTickRow":10}],"title":{"x":13,"y":5,"translationKey":"gui.ae2utility.redstone_signal_card","textColorRgb":"#413F54","modeYOffsetBelowTitleBaselinePx":4,"hintYOffsetBelowTitleBaselinePx":4,"hintYOffsetBelowModeBaselinePx":2,"hintMaxWidth":170},"modeHints":[{"translationKey":"gui.ae2utility.redstone_signal_card.hint_order","textColorRgb":"#413F54"},{"translationKey":"gui.ae2utility.redstone_signal_card.hint_craft","textColorRgb":"#413F54"},{"translationKey":"gui.ae2utility.redstone_signal_card.hint_until","textColorRgb":"#413F54"}],"modeCycleButton":{"offsetXFromPanelLeft":-19,"offsetYFromPanelTop":4},"aeSidebarStrip":{"backgroundTexture":"ae2:textures/gui/sprites/vertical_buttons_bg.png","outerWidthPx":22,"outerHeightPx":34,"rightOverlapPanelLeftPx":0,"topOffsetFromPanelTopPx":-1},"toolbarButtonPx":{"width":18,"height":20,"innerIconPx":16},"closeButton":{"offsetXFromPanelLeft":214,"offsetYFromPanelTop":-5,"width":20,"height":20,"iconOffsetX":2,"iconOffsetY":1}}
                """).getAsJsonObject();
        return new RedstoneSignalCardGuiSpecs(root);
    }
}
