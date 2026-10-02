package com.lhy.ae2utility.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.common.ForgeConfigSpec;

public final class Ae2UtilityConfigScreen extends Screen {
    private static final int MAX_ROW_WIDTH = 460;
    private static final int TOGGLE_WIDTH = 60;
    private static final int ROW_GAP = 16;
    private static final int ROW_SPACING = 6;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private int optionX;
    private int optionWidth;

    public Ae2UtilityConfigScreen(Screen parent) {
        super(Component.translatable("ae2utility.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rows.clear();
        optionWidth = Math.min(MAX_ROW_WIDTH, Math.max(220, width - 40));
        optionX = (width - optionWidth) / 2;
        int descriptionWidth = Math.max(120, optionWidth - TOGGLE_WIDTH - ROW_GAP);

        List<Ae2UtilityClientConfig.Option> options = Ae2UtilityClientConfig.options();
        List<List<FormattedCharSequence>> descriptions = new ArrayList<>(options.size());
        int totalHeight = 0;
        for (Ae2UtilityClientConfig.Option option : options) {
            List<FormattedCharSequence> lines = font.split(Component.translatable(option.key()), descriptionWidth);
            descriptions.add(lines);
            totalHeight += Math.max(BUTTON_HEIGHT, lines.size() * font.lineHeight) + ROW_SPACING;
        }

        int y = Math.max(40, (height - totalHeight) / 2);
        for (int i = 0; i < options.size(); i++) {
            Ae2UtilityClientConfig.Option option = options.get(i);
            List<FormattedCharSequence> lines = descriptions.get(i);
            int rowHeight = Math.max(BUTTON_HEIGHT, lines.size() * font.lineHeight);
            Button button = addRenderableWidget(Button.builder(stateText(option.value()), b -> {
                        option.value().set(!option.value().get());
                        b.setMessage(stateText(option.value()));
                    })
                    .bounds(optionX + optionWidth - TOGGLE_WIDTH, y + (rowHeight - BUTTON_HEIGHT) / 2,
                            TOGGLE_WIDTH, BUTTON_HEIGHT)
                    .build());
            rows.add(new Row(option, lines, y, rowHeight, button));
            y += rowHeight + ROW_SPACING;
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds((width - 200) / 2, height - 28, 200, BUTTON_HEIGHT)
                .build());
    }

    private static Component stateText(ForgeConfigSpec.BooleanValue value) {
        return Component.translatable(value.get() ? "options.on" : "options.off");
    }

    @Override
    public void render(@Nonnull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFFFF);
        for (Row row : rows) {
            int textY = row.y() + (row.height() - row.lines().size() * font.lineHeight) / 2;
            for (FormattedCharSequence line : row.lines()) {
                guiGraphics.drawString(font, line, optionX, textY, 0xFFFFFFFF, false);
                textY += font.lineHeight;
            }
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        for (Row row : rows) {
            boolean hovered = mouseX >= optionX && mouseX < optionX + optionWidth
                    && mouseY >= row.y() && mouseY < row.y() + row.height();
            if (hovered) {
                guiGraphics.renderTooltip(font,
                        font.split(Component.translatable(row.option().key() + ".tooltip"), 260),
                        mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public void onClose() {
        Ae2UtilityClientConfig.SPEC.save();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private record Row(Ae2UtilityClientConfig.Option option, List<FormattedCharSequence> lines, int y, int height,
            Button button) {
    }
}
