package com.lhy.ae2utility.jei;

import java.util.List;
import java.util.function.BooleanSupplier;

import appeng.client.gui.Icon;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.gui.elements.GuiIconToggleButton;
import mezz.jei.gui.input.UserInput;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class JeiRecipeOptionButton extends GuiIconToggleButton {
    private final Definition definition;

    public JeiRecipeOptionButton(Definition definition) {
        super(definition.offIcon(), definition.onIcon());
        this.definition = definition;
    }

    @Override
    protected void getTooltips(JeiTooltip tooltip) {
        for (Component line : definition.tooltip(definition.isOn())) {
            tooltip.add(line);
        }
    }

    @Override
    protected boolean isIconToggledOn() {
        return definition.isOn();
    }

    @Override
    protected boolean onMouseClicked(UserInput input) {
        if (!input.isSimulate()) {
            definition.press().run();
        }
        return true;
    }

    public enum Kind {
        ITEM_SUBSTITUTION,
        FLUID_SUBSTITUTION,
        BATCH_PAGE,
        BATCH_CATEGORY
    }

    public static JeiRecipeOptionButton create(Kind kind) {
        return new JeiRecipeOptionButton(switch (kind) {
            case ITEM_SUBSTITUTION -> Definition.toggle(
                    new AeIconDrawable(Icon.SUBSTITUTION_DISABLED, 12),
                    new AeIconDrawable(Icon.SUBSTITUTION_ENABLED, 12),
                    Component.translatable("jei.tooltip.ae2utility.item_substitution.disabled"),
                    Component.translatable("jei.tooltip.ae2utility.item_substitution.enabled"),
                    JeiPatternSubstitutionUi::isItemSubstituteOn,
                    JeiPatternSubstitutionUi::toggleItemSubstitute);
            case FLUID_SUBSTITUTION -> Definition.toggle(
                    new AeIconDrawable(Icon.FLUID_SUBSTITUTION_DISABLED, 12),
                    new AeIconDrawable(Icon.FLUID_SUBSTITUTION_ENABLED, 12),
                    Component.translatable("jei.tooltip.ae2utility.fluid_substitution.disabled"),
                    Component.translatable("jei.tooltip.ae2utility.fluid_substitution.enabled"),
                    JeiPatternSubstitutionUi::isFluidSubstituteOn,
                    JeiPatternSubstitutionUi::toggleFluidSubstitute);
            case BATCH_PAGE -> Definition.action(
                    new BatchIconDrawable(false),
                    Component.translatable("jei.tooltip.ae2utility.batch_encode_page"),
                    () -> JeiRecipesBatchEncode.run(true, Screen.hasShiftDown()));
            case BATCH_CATEGORY -> Definition.action(
                    new BatchIconDrawable(true),
                    Component.translatable("jei.tooltip.ae2utility.batch_encode_category"),
                    () -> JeiRecipesBatchEncode.run(false, Screen.hasShiftDown()));
        });
    }

    private record Definition(
            IDrawable offIcon,
            IDrawable onIcon,
            Component disabledTooltip,
            Component enabledTooltip,
            BooleanSupplier state,
            Runnable press,
            boolean showShiftHint) {
        static Definition toggle(IDrawable offIcon, IDrawable onIcon, Component disabledTooltip,
                Component enabledTooltip, BooleanSupplier state, Runnable press) {
            return new Definition(offIcon, onIcon, disabledTooltip, enabledTooltip, state, press, false);
        }

        static Definition action(IDrawable icon, Component tooltip, Runnable press) {
            return new Definition(icon, icon, tooltip, tooltip, () -> false, press, true);
        }

        boolean isOn() {
            return state.getAsBoolean();
        }

        List<Component> tooltip(boolean enabled) {
            Component main = enabled ? enabledTooltip : disabledTooltip;
            if (!showShiftHint) {
                return List.of(main);
            }
            return List.of(
                    main,
                    Component.translatable("jei.tooltip.ae2utility.batch_encode_shift_hint")
                            .withStyle(ChatFormatting.GRAY));
        }
    }

    private record AeIconDrawable(Icon icon, int size) implements IDrawable {
        @Override
        public int getWidth() {
            return size;
        }

        @Override
        public int getHeight() {
            return size;
        }

        @Override
        public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
            icon.getBlitter().dest(xOffset, yOffset, size, size).blit(guiGraphics);
        }
    }

    private record BatchIconDrawable(boolean category) implements IDrawable {
        private static final int SIZE = 8;

        @Override
        public int getWidth() {
            return SIZE;
        }

        @Override
        public int getHeight() {
            return SIZE;
        }

        @Override
        public void draw(GuiGraphics guiGraphics, int x, int y) {
            if (category) {
                drawCategoryIcon(guiGraphics, x, y);
            } else {
                drawPageIcon(guiGraphics, x, y);
            }
        }

        private static void drawPageIcon(GuiGraphics guiGraphics, int x, int y) {
            guiGraphics.fill(x, y, x + SIZE, y + SIZE, 0xFFFFFFFF);
            guiGraphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, 0xFF555555);
            guiGraphics.fill(x + 3, y + 1, x + 5, y + SIZE - 1, 0xFFFFFFFF);
            guiGraphics.fill(x + 2, y + 3, x + 6, y + 5, 0xFFFFFFFF);
        }

        private static void drawCategoryIcon(GuiGraphics guiGraphics, int x, int y) {
            guiGraphics.fill(x, y, x + 3, y + 3, 0xFFFFFFFF);
            guiGraphics.fill(x + 5, y, x + 8, y + 3, 0xFFFFFFFF);
            guiGraphics.fill(x, y + 5, x + 3, y + 8, 0xFFFFFFFF);
            guiGraphics.fill(x + 5, y + 5, x + 8, y + 8, 0xFFFFFFFF);
            guiGraphics.fill(x + 3, y + 1, x + 5, y + 7, 0xFFFFFFFF);
        }
    }
}
