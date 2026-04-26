package com.lhy.ae2utility.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import appeng.client.gui.Icon;
import appeng.integration.modules.curios.CuriosIntegration;
import appeng.menu.me.items.PatternEncodingTermMenu;

import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.common.util.ImmutableRect2i;

/**
 * JEI 配方界面左上角（整页一次）的物品/流体替换开关，与样板编码终端行为一致。
 */
public final class JeiPatternSubstitutionUi {
    /** 无线/非终端场景下使用的本地开关（与终端打开时由菜单同步） */
    public static boolean localSubstitute = false;
    public static boolean localSubstituteFluids = true;
    private static boolean localStateLoaded = false;
    private static final String CONFIG_FILE_NAME = "ae2utility-jei-substitution.properties";

    private static final int BTN = 8;
    private static final int GAP = 2;
    private static final int PAD = 2;

    private static int lastItemX = Integer.MIN_VALUE;
    private static int lastItemY = Integer.MIN_VALUE;
    private static int lastFluidX = Integer.MIN_VALUE;
    private static int lastFluidY = Integer.MIN_VALUE;

    private JeiPatternSubstitutionUi() {}

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

    private static @Nullable PatternEncodingTermMenu getOpenPatternMenu() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof PatternEncodingTermMenu menu) {
            return menu;
        }
        return null;
    }

    public static boolean isSubstitutionContextActive() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        if (player.containerMenu instanceof PatternEncodingTermMenu) {
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
        var menu = getOpenPatternMenu();
        if (menu != null) {
            return menu.isSubstitute();
        }
        return localSubstitute;
    }

    public static boolean isFluidSubstituteOn() {
        ensureLocalStateLoaded();
        var menu = getOpenPatternMenu();
        if (menu != null) {
            return menu.isSubstituteFluids();
        }
        return localSubstituteFluids;
    }

    public static void toggleItemSubstitute() {
        ensureLocalStateLoaded();
        var menu = getOpenPatternMenu();
        if (menu != null) {
            menu.setSubstitute(!menu.isSubstitute());
            localSubstitute = menu.isSubstitute();
        } else {
            localSubstitute = !localSubstitute;
        }
        saveLocalState();
    }

    public static void toggleFluidSubstitute() {
        ensureLocalStateLoaded();
        var menu = getOpenPatternMenu();
        if (menu != null) {
            menu.setSubstituteFluids(!menu.isSubstituteFluids());
            localSubstituteFluids = menu.isSubstituteFluids();
        } else {
            localSubstituteFluids = !localSubstituteFluids;
        }
        saveLocalState();
    }

    /**
     * 在 JEI {@code RecipesGui} 渲染末尾调用：仅在配方区域左上角绘制一对 8×8 开关。
     */
    public static void render(ImmutableRect2i recipeLayoutsArea, GuiGraphics guiGraphics, int mouseX, int mouseY) {
        ensureLocalStateLoaded();
        if (!isSubstitutionContextActive()) {
            lastItemX = Integer.MIN_VALUE;
            return;
        }

        int x = recipeLayoutsArea.getX() + PAD;
        int y = recipeLayoutsArea.getY() + PAD;

        lastItemX = x;
        lastItemY = y;
        lastFluidX = x;
        lastFluidY = y + BTN + GAP;

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

        if (mouseX >= x && mouseX < x + BTN && mouseY >= y && mouseY < y + BTN) {
            drawItemTooltip(guiGraphics, mouseX, mouseY);
        } else if (mouseX >= lastFluidX && mouseX < lastFluidX + BTN && mouseY >= lastFluidY && mouseY < lastFluidY + BTN) {
            drawFluidTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    private static void drawItemTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>();
        if (isItemSubstituteOn()) {
            lines.add(Component.translatable("gui.tooltips.ae2.SubstitutionsOn"));
            lines.add(Component.translatable("gui.tooltips.ae2.SubstitutionsDescEnabled").withStyle(net.minecraft.ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("gui.tooltips.ae2.SubstitutionsOff"));
            lines.add(Component.translatable("gui.tooltips.ae2.SubstitutionsDescDisabled").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        guiGraphics.renderTooltip(mc.font, lines, java.util.Optional.empty(), mouseX, mouseY);
    }

    private static void drawFluidTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.tooltips.ae2.FluidSubstitutions"));
        if (isFluidSubstituteOn()) {
            lines.add(Component.translatable("gui.tooltips.ae2.FluidSubstitutionsDescEnabled").withStyle(net.minecraft.ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("gui.tooltips.ae2.FluidSubstitutionsDescDisabled").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        guiGraphics.renderTooltip(mc.font, lines, java.util.Optional.empty(), mouseX, mouseY);
    }

    public static boolean handleClick(double mouseX, double mouseY) {
        if (!isSubstitutionContextActive() || lastItemX == Integer.MIN_VALUE) {
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
        return false;
    }
}
