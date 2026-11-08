package com.lhy.ae2utility.item;

import java.util.List;

import javax.annotation.Nullable;

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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import appeng.items.materials.UpgradeCardItem;

import com.lhy.ae2utility.card.RedstoneSignalCardMode;

public class RedstoneSignalCardItem extends UpgradeCardItem {
    public static final int DEFAULT_PULSE_DURATION_TICKS = 6;
    public static final int DEFAULT_HOLD_TICKS = 20;
    public static final int MIN_HOLD_TICKS = 2;
    public static final int MAX_HOLD_TICKS = 72000;
    public static final String MODE_TAG = "Ae2UtilityRedstoneMode";
    public static final String HOLD_TAG = "Ae2UtilityRedstoneHoldTicks";

    public RedstoneSignalCardItem(Properties properties) {
        super(properties);
    }

    public static int defaultHoldTicks() {
        return DEFAULT_HOLD_TICKS;
    }

    public static int clampHoldTicks(int ticks) {
        return Mth.clamp(ticks, MIN_HOLD_TICKS, MAX_HOLD_TICKS);
    }

    public static RedstoneSignalCardMode getMode(ItemStack stack) {
        return RedstoneSignalCardMode.byName(stack.getOrCreateTag().getString(MODE_TAG));
    }

    public static void setMode(ItemStack stack, RedstoneSignalCardMode mode) {
        stack.getOrCreateTag().putString(MODE_TAG, mode.getSerializedName());
    }

    public static int getHoldTicks(ItemStack stack) {
        int ticks = stack.getOrCreateTag().getInt(HOLD_TAG);
        return ticks <= 0 ? DEFAULT_HOLD_TICKS : clampHoldTicks(ticks);
    }

    public static void setHoldTicks(ItemStack stack, int ticks) {
        stack.getOrCreateTag().putInt(HOLD_TAG, clampHoldTicks(ticks));
    }

    public static int resolveOutputDurationTicks(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof RedstoneSignalCardItem)) {
            return DEFAULT_PULSE_DURATION_TICKS;
        }
        int ticks = getHoldTicks(stack);
        return ticks <= MIN_HOLD_TICKS ? DEFAULT_PULSE_DURATION_TICKS : ticks;
    }

    public static int modeDisplayColor(RedstoneSignalCardMode mode) {
        return switch (mode) {
            case ORDER -> 0x55AA55;
            case CRAFT -> 0x4A78D0;
            case UNTIL_RECIPE_COMPLETE -> 0xD04A4A;
        };
    }

    public static String formatIntervalText(int ticks) {
        ticks = clampHoldTicks(ticks);
        if (ticks <= 60) {
            return ticks + "tick";
        }
        if (ticks <= 1200) {
            return (ticks / 20) + "秒";
        }
        return (ticks / 1200) + "分";
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() != this) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.lhy.ae2utility.client.gui.RedstoneSignalCardPanelScreen.open(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        RedstoneSignalCardMode mode = getMode(stack);
        MutableComponent modeLine = Component.translatable("item.ae2utility.redstone_signal_card.tooltip_mode_prefix")
                .append(mode.getDisplayName().copy().withStyle(style -> style.withColor(modeDisplayColor(mode))));
        tooltip.add(modeLine);
        if (mode != RedstoneSignalCardMode.UNTIL_RECIPE_COMPLETE) {
            tooltip.add(Component.translatable("item.ae2utility.redstone_signal_card.tooltip_interval_prefix")
                    .append(Component.literal(formatIntervalText(getHoldTicks(stack)))
                            .withStyle(style -> style.withColor(0xD04A4A))));
        }
        tooltip.add(Component.translatable("item.ae2utility.redstone_signal_card.tooltip_hint")
                .withStyle(ChatFormatting.BLUE));
    }
}
