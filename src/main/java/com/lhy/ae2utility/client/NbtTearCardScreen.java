package com.lhy.ae2utility.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import com.lhy.ae2utility.jei.NbtTearCardGhostHandler;
import com.lhy.ae2utility.menu.NbtTearCardMenu;

public class NbtTearCardScreen extends AbstractContainerScreen<NbtTearCardMenu> {
    /** 与 Ae2TerminalRecipeTransferHandler 中 JEI 槽位高亮一致的透明蓝 */
    public static final int AE_BLUE_SLOT_HIGHLIGHT = 0x400000FF;

    /** 与潜影盒界面一致（上半 3×9 + 下半玩家栏） */
    private static final ResourceLocation SHULKER_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/shulker_box.png");

    public NbtTearCardScreen(NbtTearCardMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 168;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.blit(SHULKER_TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (NbtTearCardGhostHandler.isFilterGhostDragActive()) {
            int idx = hoveredFilterSlotIndex();
            if (idx >= 0) {
                Slot s = this.menu.getSlot(idx);
                int ax = this.leftPos + s.x;
                int ay = this.topPos + s.y;
                graphics.fill(RenderType.guiOverlay(), ax, ay, ax + 18, ay + 18, AE_BLUE_SLOT_HIGHLIGHT);
            }
        }
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    private int hoveredFilterSlotIndex() {
        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        double mx = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / Math.max(1, window.getScreenWidth());
        double my = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / Math.max(1, window.getScreenHeight());
        for (int i = 0; i < NbtTearCardMenu.FILTER_SIZE; i++) {
            Slot slot = this.menu.getSlot(i);
            int sx = this.leftPos + slot.x;
            int sy = this.topPos + slot.y;
            if (mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void removed() {
        NbtTearCardGhostHandler.clearFilterGhostDragActive();
        super.removed();
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }
}
