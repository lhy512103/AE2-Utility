package com.lhy.ae2utility.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class Ae2UtilityConfigScreen extends Screen {
    private static final int MAX_ROW_WIDTH = 460;
    private static final int TOGGLE_WIDTH = 60;
    private static final int ROW_GAP = 16;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private Button encodeWithoutTerminalButton;
    private List<FormattedCharSequence> encodeWithoutTerminalDescription = List.of();
    private int optionX;
    private int optionY;
    private int optionWidth;
    private int optionHeight;

    public Ae2UtilityConfigScreen(Screen parent) {
        super(Component.translatable("ae2utility.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        optionWidth = Math.min(MAX_ROW_WIDTH, Math.max(220, width - 40));
        optionX = (width - optionWidth) / 2;
        int descriptionWidth = Math.max(120, optionWidth - TOGGLE_WIDTH - ROW_GAP);
        encodeWithoutTerminalDescription = font.split(
                Component.translatable("ae2utility.config.allowJeiPatternEncodeWithoutOpenTerminal"),
                descriptionWidth);
        optionHeight = Math.max(BUTTON_HEIGHT, encodeWithoutTerminalDescription.size() * font.lineHeight);
        optionY = Math.max(52, height / 2 - optionHeight / 2 - 12);

        encodeWithoutTerminalButton = addRenderableWidget(Button.builder(
                encodeWithoutTerminalState(),
                button -> toggleEncodeWithoutTerminal())
                .bounds(optionX + optionWidth - TOGGLE_WIDTH, optionY + (optionHeight - BUTTON_HEIGHT) / 2,
                        TOGGLE_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds((width - 200) / 2, height - 28, 200, BUTTON_HEIGHT)
                .build());
    }

    private void toggleEncodeWithoutTerminal() {
        boolean next = !Ae2UtilityClientConfig.allowJeiPatternEncodeWithoutOpenTerminal();
        Ae2UtilityClientConfig.ALLOW_JEI_PATTERN_ENCODE_WITHOUT_OPEN_TERMINAL.set(next);
        encodeWithoutTerminalButton.setMessage(encodeWithoutTerminalState());
    }

    private Component encodeWithoutTerminalState() {
        boolean enabled = Ae2UtilityClientConfig.allowJeiPatternEncodeWithoutOpenTerminal();
        return Component.translatable(enabled ? "options.on" : "options.off");
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFFFF);
        int textY = optionY + (optionHeight - encodeWithoutTerminalDescription.size() * font.lineHeight) / 2;
        for (FormattedCharSequence line : encodeWithoutTerminalDescription) {
            guiGraphics.drawString(font, line, optionX, textY, 0xFFFFFFFF, false);
            textY += font.lineHeight;
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        boolean optionHovered = mouseX >= optionX && mouseX < optionX + optionWidth
                && mouseY >= optionY && mouseY < optionY + optionHeight;
        if (optionHovered) {
            guiGraphics.renderComponentTooltip(
                    font,
                    List.of(Component.translatable(
                            "ae2utility.config.allowJeiPatternEncodeWithoutOpenTerminal.tooltip")),
                    mouseX,
                    mouseY);
        }
    }

    @Override
    public void onClose() {
        Ae2UtilityClientConfig.SPEC.save();
        minecraft.setScreen(parent);
    }
}
