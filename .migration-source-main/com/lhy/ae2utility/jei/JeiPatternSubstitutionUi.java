package com.lhy.ae2utility.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.nio.file.Files;
import java.nio.file.Path;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.client.Ae2UtilityClientConfig;
import com.lhy.ae2utility.compat.WcwtCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.sounds.SoundEvents;

import org.jetbrains.annotations.Nullable;

import appeng.client.gui.Icon;
import appeng.integration.modules.curios.CuriosIntegration;

import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.inputs.IJeiUserInput;

/**
 * JEI 配方选项按钮：由 MixinRecipeOptionButtons 追加到 JEI 原生的两个排序按钮之后。
 */
public final class JeiPatternSubstitutionUi {
    /** Buttons appended to JEI's native recipe-option tab by MixinRecipeOptionButtons. */
    public enum RecipeOptionButton {
        ITEM_SUBSTITUTION,
        FLUID_SUBSTITUTION,
        BATCH_PAGE,
        BATCH_CATEGORY
    }

    public static final class RecipeOptionButtonController implements IIconButtonController {
        private final RecipeOptionButton button;

        public RecipeOptionButtonController(RecipeOptionButton button) {
            this.button = button;
        }

        @Override
        public boolean onPress(IJeiUserInput input) {
            if (input.isSimulate() || !isSubstitutionContextActive()) {
                return false;
            }
            switch (button) {
                case ITEM_SUBSTITUTION -> toggleItemSubstitute();
                case FLUID_SUBSTITUTION -> toggleFluidSubstitute();
                case BATCH_PAGE -> JeiRecipesBatchEncode.run(true, Screen.hasShiftDown());
                case BATCH_CATEGORY -> JeiRecipesBatchEncode.run(false, Screen.hasShiftDown());
            }
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        @Override
        public void updateState(IButtonState state) {
            boolean active = isSubstitutionContextActive();
            state.setVisible(active && (button != RecipeOptionButton.BATCH_CATEGORY
                    || Ae2UtilityClientConfig.showJeiBatchEncodeFullCategoryButton()));
            state.setActive(active);
            if (button == RecipeOptionButton.ITEM_SUBSTITUTION) {
                state.setForcePressed(active && isItemSubstituteOn());
            } else if (button == RecipeOptionButton.FLUID_SUBSTITUTION) {
                state.setForcePressed(active && isFluidSubstituteOn());
            }
        }

        @Override
        public void getTooltips(mezz.jei.api.gui.builder.ITooltipBuilder tooltip) {
            switch (button) {
                case ITEM_SUBSTITUTION -> {
                    tooltip.add(Component.translatable(isItemSubstituteOn()
                            ? "gui.tooltips.ae2.SubstitutionsOn" : "gui.tooltips.ae2.SubstitutionsOff"));
                    tooltip.add(Component.translatable(isItemSubstituteOn()
                            ? "gui.tooltips.ae2.SubstitutionsDescEnabled"
                            : "gui.tooltips.ae2.SubstitutionsDescDisabled").withStyle(ChatFormatting.GRAY));
                }
                case FLUID_SUBSTITUTION -> {
                    tooltip.add(Component.translatable("gui.tooltips.ae2.FluidSubstitutions"));
                    tooltip.add(Component.translatable(isFluidSubstituteOn()
                            ? "gui.tooltips.ae2.FluidSubstitutionsDescEnabled"
                            : "gui.tooltips.ae2.FluidSubstitutionsDescDisabled").withStyle(ChatFormatting.GRAY));
                }
                case BATCH_PAGE -> {
                    tooltip.add(Component.translatable("jei.tooltip.ae2utility.batch_encode_page"));
                    tooltip.add(Component.translatable("jei.tooltip.ae2utility.batch_encode_shift_hint").withStyle(ChatFormatting.GRAY));
                }
                case BATCH_CATEGORY -> {
                    tooltip.add(Component.translatable("jei.tooltip.ae2utility.batch_encode_category"));
                    tooltip.add(Component.translatable("jei.tooltip.ae2utility.batch_encode_shift_hint").withStyle(ChatFormatting.GRAY));
                }
            }
        }

        @Override
        public void drawExtras(GuiGraphics graphics, Rect2i area, int mouseX, int mouseY, float partialTicks) {
            int x = area.getX();
            int y = area.getY();
            switch (button) {
                case ITEM_SUBSTITUTION -> (isItemSubstituteOn()
                        ? Icon.S_SUBSTITUTION_ENABLED : Icon.S_SUBSTITUTION_DISABLED)
                        .getBlitter().dest(x, y, 16, 16).blit(graphics);
                case FLUID_SUBSTITUTION -> (isFluidSubstituteOn()
                        ? Icon.S_FLUID_SUBSTITUTION_ENABLED : Icon.S_FLUID_SUBSTITUTION_DISABLED)
                        .getBlitter().dest(x, y, 16, 16).blit(graphics);
                case BATCH_PAGE -> drawBatchIcon(graphics, TEX_BATCH_PAGE, x, y);
                case BATCH_CATEGORY -> drawBatchIcon(graphics, TEX_BATCH_CATEGORY, x, y);
            }
        }

        private static void drawBatchIcon(GuiGraphics graphics, ResourceLocation texture, int x, int y) {
            graphics.pose().pushPose();
            graphics.pose().translate(x + 8, y + 8, 0);
            graphics.pose().scale(2.0F, 2.0F, 1.0F);
            graphics.blit(texture, -4, -4, 0, 0, 8, 8, 16, 16);
            graphics.pose().popPose();
        }
    }
    /** 无线/非终端场景下使用的本地开关（与终端打开时由菜单同步） */
    public static boolean localSubstitute = false;
    public static boolean localSubstituteFluids = true;
    private static boolean localStateLoaded = false;
    private static final String CONFIG_FILE_NAME = "ae2utility-jei-substitution.properties";

    private static final int BTN = 8;
    private static final int GAP = 2;
    private static final int PAD = 2;
    private static final int BATCH_ICON_SHEET_W = 16;
    private static final int BATCH_ICON_SHEET_H = 16;

    /** 须为仅含小写/数字/下划线的路径；中文文件名会在 {@link ResourceLocation} 构造时直接抛错并拖垮 JEI 渲染。 */
    private static final ResourceLocation TEX_BATCH_PAGE =
            ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "textures/gui/batch_encode_page.png");
    private static final ResourceLocation TEX_BATCH_CATEGORY =
            ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "textures/gui/batch_encode_category.png");

    // Retained for compatibility with the old layout helpers; the active path uses JEI's native option-button layout.
    private static int lastItemX = Integer.MIN_VALUE;
    private static int lastItemY = Integer.MIN_VALUE;
    private static int lastFluidX = Integer.MIN_VALUE;
    private static int lastFluidY = Integer.MIN_VALUE;
    private static int lastBatchPageX = Integer.MIN_VALUE;
    private static int lastBatchPageY = Integer.MIN_VALUE;
    private static int lastBatchMachineX = Integer.MIN_VALUE;
    private static int lastBatchMachineY = Integer.MIN_VALUE;

    private JeiPatternSubstitutionUi() {}

    /**
     * 与 {@link com.lhy.ae2utility.mixin.MixinRecipesGui} 中配方区域一致；在鼠标命中检测前调用，避免仅依赖 Render 延后写入的坐标。
     */
    public static void syncLayoutFromRecipeArea(ImmutableRect2i recipeLayoutsArea) {
        if (!isSubstitutionContextActive()) {
            clearLayoutHitBoxes();
            return;
        }

        int x = recipeLayoutsArea.getX() + PAD;
        int y = recipeLayoutsArea.getY() + PAD;

        lastItemX = x;
        lastItemY = y;
        lastFluidX = x;
        lastFluidY = y + BTN + GAP;
        lastBatchPageX = lastFluidX;
        lastBatchPageY = lastFluidY + BTN + GAP;
        if (Ae2UtilityClientConfig.showJeiBatchEncodeFullCategoryButton()) {
            lastBatchMachineX = lastBatchPageX;
            lastBatchMachineY = lastBatchPageY + BTN + GAP;
        } else {
            lastBatchMachineX = Integer.MIN_VALUE;
            lastBatchMachineY = Integer.MIN_VALUE;
        }
    }

    private static void clearLayoutHitBoxes() {
        lastItemX = Integer.MIN_VALUE;
        lastItemY = Integer.MIN_VALUE;
        lastFluidX = Integer.MIN_VALUE;
        lastFluidY = Integer.MIN_VALUE;
        lastBatchPageX = Integer.MIN_VALUE;
        lastBatchPageY = Integer.MIN_VALUE;
        lastBatchMachineX = Integer.MIN_VALUE;
        lastBatchMachineY = Integer.MIN_VALUE;
    }

    private static void ensureLocalStateLoaded() {
        if (localStateLoaded) {
            return;
        }
        localStateLoaded = true;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameDirectory == null) {
            return;
        }
        Path path = getLocalStatePath(minecraft);
        if (!Files.exists(path)) {
            return;
        }
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
            properties.load(reader);
            localSubstitute = Boolean.parseBoolean(properties.getProperty("itemSubstitute", Boolean.toString(localSubstitute)));
            localSubstituteFluids = Boolean.parseBoolean(properties.getProperty("fluidSubstitute", Boolean.toString(localSubstituteFluids)));
        } catch (Exception ignored) {
        }
    }

    private static void saveLocalState() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameDirectory == null) {
            return;
        }
        Properties properties = new Properties();
        properties.setProperty("itemSubstitute", Boolean.toString(localSubstitute));
        properties.setProperty("fluidSubstitute", Boolean.toString(localSubstituteFluids));
        Path path = getLocalStatePath(minecraft);
        try {
            Files.createDirectories(path.getParent());
            try (var writer = Files.newBufferedWriter(path)) {
                properties.store(writer, "AE2 Utility JEI substitution toggles");
            }
        } catch (Exception ignored) {
        }
    }

    private static Path getLocalStatePath(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath().resolve("config").resolve(CONFIG_FILE_NAME);
    }

    private static @Nullable Object getOpenPatternMenu() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && WcwtCompat.isPatternEncodingLikeMenu(player.containerMenu)) {
            return player.containerMenu;
        }
        return null;
    }

    public static boolean isSubstitutionContextActive() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        if (WcwtCompat.isPatternEncodingLikeMenu(player.containerMenu)) {
            return true;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            String itemName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getInventory().getItem(i).getItem()).toString();
            if (itemName.contains("pattern_encoding") || itemName.contains("universal_terminal")) {
                return true;
            }
        }
        var curios = player.getCapability(CuriosIntegration.ITEM_HANDLER);
        if (curios != null) {
            for (int slot = 0; slot < curios.getSlots(); slot++) {
                String itemName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(curios.getStackInSlot(slot).getItem()).toString();
                if (itemName.contains("pattern_encoding") || itemName.contains("universal_terminal")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isItemSubstituteOn() {
        ensureLocalStateLoaded();
        Object menu = getOpenPatternMenu();
        if (menu != null) {
            Boolean value = firstNonNullBoolean(menu, "isSubstitute", "isPatternSubstitute");
            if (value != null) {
                return value.booleanValue();
            }
        }
        return localSubstitute;
    }

    public static boolean isFluidSubstituteOn() {
        ensureLocalStateLoaded();
        Object menu = getOpenPatternMenu();
        if (menu != null) {
            Boolean value = firstNonNullBoolean(menu, "isSubstituteFluids", "isPatternFluidSubstitute");
            if (value != null) {
                return value.booleanValue();
            }
        }
        return localSubstituteFluids;
    }

    public static void toggleItemSubstitute() {
        ensureLocalStateLoaded();
        Object menu = getOpenPatternMenu();
        if (menu != null) {
            boolean next = !isItemSubstituteOn();
            boolean applied = invokeVoidBoolean(menu, "setSubstitute", next)
                    || invokeVoidBoolean(menu, "setPatternSubstitute", next);
            if (applied) {
                localSubstitute = isItemSubstituteOn();
            } else {
                localSubstitute = next;
            }
        } else {
            localSubstitute = !localSubstitute;
        }
        saveLocalState();
    }

    public static void toggleFluidSubstitute() {
        ensureLocalStateLoaded();
        Object menu = getOpenPatternMenu();
        if (menu != null) {
            boolean next = !isFluidSubstituteOn();
            boolean applied = invokeVoidBoolean(menu, "setSubstituteFluids", next)
                    || invokeVoidBoolean(menu, "setPatternFluidSubstitute", next);
            if (applied) {
                localSubstituteFluids = isFluidSubstituteOn();
            } else {
                localSubstituteFluids = next;
            }
        } else {
            localSubstituteFluids = !localSubstituteFluids;
        }
        saveLocalState();
    }

    private static @Nullable Boolean invokeBoolean(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value instanceof Boolean b ? b : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable Boolean firstNonNullBoolean(Object target, String... methodNames) {
        for (String name : methodNames) {
            Boolean v = invokeBoolean(target, name);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /** @return 是否成功调用到对应 setter（AE2 样板终端与 WCWT 方法名不同） */
    private static boolean invokeVoidBoolean(Object target, String methodName, boolean value) {
        try {
            target.getClass().getMethod(methodName, boolean.class).invoke(target, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void blitBatchIcon(GuiGraphics g, ResourceLocation tex, int destX, int destY, int mouseX, int mouseY) {
        boolean hovered = mouseX >= destX && mouseX < destX + BTN && mouseY >= destY && mouseY < destY + BTN;
        int u = hovered ? BTN : 0;
        g.blit(tex, destX, destY, u, 0, BTN, BTN, BATCH_ICON_SHEET_W, BATCH_ICON_SHEET_H);
    }

    /**
     * 在 JEI {@code RecipesGui} 渲染末尾调用：配方区域左上角绘制替换开关与同尺寸批量编码按钮。
     */
    public static void render(ImmutableRect2i recipeLayoutsArea, GuiGraphics guiGraphics, int mouseX, int mouseY) {
        ensureLocalStateLoaded();
        syncLayoutFromRecipeArea(recipeLayoutsArea);
        if (!isSubstitutionContextActive()) {
            return;
        }

        int x = lastItemX;
        int y = lastItemY;

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();

        if (isItemSubstituteOn()) {
            Icon.S_SUBSTITUTION_ENABLED.getBlitter().dest(x, y, BTN, BTN).blit(guiGraphics);
        } else {
            Icon.S_SUBSTITUTION_DISABLED.getBlitter().dest(x, y, BTN, BTN).blit(guiGraphics);
        }

        if (isFluidSubstituteOn()) {
            Icon.S_FLUID_SUBSTITUTION_ENABLED.getBlitter().dest(lastFluidX, lastFluidY, BTN, BTN).blit(guiGraphics);
        } else {
            Icon.S_FLUID_SUBSTITUTION_DISABLED.getBlitter().dest(lastFluidX, lastFluidY, BTN, BTN).blit(guiGraphics);
        }

        blitBatchIcon(guiGraphics, TEX_BATCH_PAGE, lastBatchPageX, lastBatchPageY, mouseX, mouseY);
        if (Ae2UtilityClientConfig.showJeiBatchEncodeFullCategoryButton()) {
            blitBatchIcon(guiGraphics, TEX_BATCH_CATEGORY, lastBatchMachineX, lastBatchMachineY, mouseX, mouseY);
        }

        if (mouseX >= x && mouseX < x + BTN && mouseY >= y && mouseY < y + BTN) {
            drawItemTooltip(guiGraphics, mouseX, mouseY);
        } else if (mouseX >= lastFluidX && mouseX < lastFluidX + BTN && mouseY >= lastFluidY && mouseY < lastFluidY + BTN) {
            drawFluidTooltip(guiGraphics, mouseX, mouseY);
        } else if (mouseX >= lastBatchPageX && mouseX < lastBatchPageX + BTN && mouseY >= lastBatchPageY && mouseY < lastBatchPageY + BTN) {
            drawBatchPageTooltip(guiGraphics, mouseX, mouseY);
        } else if (Ae2UtilityClientConfig.showJeiBatchEncodeFullCategoryButton()
                && mouseX >= lastBatchMachineX && mouseX < lastBatchMachineX + BTN && mouseY >= lastBatchMachineY
                && mouseY < lastBatchMachineY + BTN) {
            drawBatchMachineTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    private static void drawItemTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>();
        if (isItemSubstituteOn()) {
            lines.add(Component.translatable("gui.tooltips.ae2.SubstitutionsOn"));
            lines.add(Component.translatable("gui.tooltips.ae2.SubstitutionsDescEnabled").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("gui.tooltips.ae2.SubstitutionsOff"));
            lines.add(Component.translatable("gui.tooltips.ae2.SubstitutionsDescDisabled").withStyle(ChatFormatting.GRAY));
        }
        guiGraphics.renderTooltip(mc.font, lines, java.util.Optional.empty(), mouseX, mouseY);
    }

    private static void drawFluidTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.tooltips.ae2.FluidSubstitutions"));
        if (isFluidSubstituteOn()) {
            lines.add(Component.translatable("gui.tooltips.ae2.FluidSubstitutionsDescEnabled").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("gui.tooltips.ae2.FluidSubstitutionsDescDisabled").withStyle(ChatFormatting.GRAY));
        }
        guiGraphics.renderTooltip(mc.font, lines, java.util.Optional.empty(), mouseX, mouseY);
    }

    private static void drawBatchPageTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("jei.tooltip.ae2utility.batch_encode_page"));
        lines.add(Component.translatable("jei.tooltip.ae2utility.batch_encode_shift_hint").withStyle(ChatFormatting.GRAY));
        guiGraphics.renderTooltip(mc.font, lines, java.util.Optional.empty(), mouseX, mouseY);
    }

    private static void drawBatchMachineTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("jei.tooltip.ae2utility.batch_encode_category"));
        lines.add(Component.translatable("jei.tooltip.ae2utility.batch_encode_shift_hint").withStyle(ChatFormatting.GRAY));
        guiGraphics.renderTooltip(mc.font, lines, java.util.Optional.empty(), mouseX, mouseY);
    }

    public static boolean handleClick(double mouseX, double mouseY) {
        if (!isSubstitutionContextActive()) {
            return false;
        }
        if (lastItemX == Integer.MIN_VALUE) {
            return false;
        }
        if (mouseX >= lastItemX && mouseX < lastItemX + BTN && mouseY >= lastItemY && mouseY < lastItemY + BTN) {
            toggleItemSubstitute();
            return true;
        }
        if (mouseX >= lastFluidX && mouseX < lastFluidX + BTN && mouseY >= lastFluidY && mouseY < lastFluidY + BTN) {
            toggleFluidSubstitute();
            return true;
        }
        if (mouseX >= lastBatchPageX && mouseX < lastBatchPageX + BTN
                && mouseY >= lastBatchPageY && mouseY < lastBatchPageY + BTN) {
            JeiRecipesBatchEncode.run(true, Screen.hasShiftDown());
            return true;
        }
        if (Ae2UtilityClientConfig.showJeiBatchEncodeFullCategoryButton()
                && mouseX >= lastBatchMachineX && mouseX < lastBatchMachineX + BTN
                && mouseY >= lastBatchMachineY && mouseY < lastBatchMachineY + BTN) {
            JeiRecipesBatchEncode.run(false, Screen.hasShiftDown());
            return true;
        }
        return false;
    }
}
