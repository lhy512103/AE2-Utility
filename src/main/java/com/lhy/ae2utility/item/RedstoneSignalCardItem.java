package com.lhy.ae2utility.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import appeng.items.materials.UpgradeCardItem;

import com.lhy.ae2utility.card.RedstoneSignalCardMode;
import com.lhy.ae2utility.client.gui.RedstoneSignalCardPanelScreen;
import com.lhy.ae2utility.init.ModDataComponents;

public class RedstoneSignalCardItem extends UpgradeCardItem {
    public static final int DEFAULT_PULSE_DURATION_TICKS = 6;
    public static final int DEFAULT_HOLD_TICKS = 20;
    public static final int MIN_HOLD_TICKS = 2;
    public static final int MAX_HOLD_TICKS = 72000;

    public RedstoneSignalCardItem(Properties properties) {
        super(properties.component(ModDataComponents.REDSTONE_SIGNAL_CARD_MODE, RedstoneSignalCardMode.ORDER)
                .component(ModDataComponents.REDSTONE_SIGNAL_HOLD_TICKS, DEFAULT_HOLD_TICKS));
    }

    public static int defaultHoldTicks() {
        return DEFAULT_HOLD_TICKS;
    }

    public static int clampHoldTicks(int ticks) {
        return Mth.clamp(ticks, MIN_HOLD_TICKS, MAX_HOLD_TICKS);
    }

    public static int effectiveHoldTicksForDisplay(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.REDSTONE_SIGNAL_HOLD_TICKS, DEFAULT_HOLD_TICKS);
    }

    public static int modeDisplayColor(RedstoneSignalCardMode mode) {
        return switch (mode) {
            case ORDER -> 0x55AA55;
            case CRAFT -> 0x4A78D0;
            case UNTIL_RECIPE_COMPLETE -> 0xD04A4A;
        };
    }

    public static int intervalDisplayColor() {
        return 0xD04A4A;
    }

    public static String formatIntervalText(int ticks) {
        ticks = clampHoldTicks(ticks);
        if (ticks <= 60) {
            return ticks + "tick";
        }
        if (ticks <= 60 * 20) {
            return (ticks / 20) + "秒";
        }
        return (ticks / (60 * 20)) + "分";
    }

    public static int resolveOutputDurationTicks(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof RedstoneSignalCardItem)) {
            return DEFAULT_PULSE_DURATION_TICKS;
        }
        int ticks = clampHoldTicks(stack.getOrDefault(ModDataComponents.REDSTONE_SIGNAL_HOLD_TICKS, DEFAULT_HOLD_TICKS));
        if (ticks <= MIN_HOLD_TICKS) {
            return DEFAULT_PULSE_DURATION_TICKS;
        }
        return ticks;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() != this) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide()) {
            RedstoneSignalCardPanelScreen.open(hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        RedstoneSignalCardMode mode = stack.getOrDefault(ModDataComponents.REDSTONE_SIGNAL_CARD_MODE,
                RedstoneSignalCardMode.ORDER);
        int ticks = effectiveHoldTicksForDisplay(stack);

        MutableComponent modeLine = Component.translatable("item.ae2utility.redstone_signal_card.tooltip_mode_prefix")
                .append(mode.getDisplayName().copy().withColor(modeDisplayColor(mode)));

        tooltipComponents.add(modeLine);
        if (mode != RedstoneSignalCardMode.UNTIL_RECIPE_COMPLETE) {
            MutableComponent intervalLine =
                    Component.translatable("item.ae2utility.redstone_signal_card.tooltip_interval_prefix")
                            .append(Component.literal(formatIntervalText(ticks)).withColor(intervalDisplayColor()));
            tooltipComponents.add(intervalLine);
        }
        tooltipComponents.add(Component.translatable("item.ae2utility.redstone_signal_card.tooltip_hint")
                .withStyle(ChatFormatting.BLUE));
    }
}
