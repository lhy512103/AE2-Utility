package com.lhy.ae2utility.item;

import java.util.List;
import javax.annotation.Nullable;
import com.lhy.ae2utility.card.RedstoneSignalCardMode;
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

/** Configurable AE2 pattern-provider redstone card. 1.20.1 stores settings in legacy tag. */
public class RedstoneSignalCardItem extends UpgradeCardItem {
    public static final int DEFAULT_PULSE_DURATION_TICKS = 6, DEFAULT_HOLD_TICKS = 20, MIN_HOLD_TICKS = 2, MAX_HOLD_TICKS = 72000;
    public static final String MODE_TAG = "Ae2UtilityRedstoneMode", HOLD_TAG = "Ae2UtilityRedstoneHoldTicks";
    public RedstoneSignalCardItem(Properties properties) { super(properties); }
    public static int defaultHoldTicks() { return DEFAULT_HOLD_TICKS; }
    public static int clampHoldTicks(int ticks) { return Mth.clamp(ticks, MIN_HOLD_TICKS, MAX_HOLD_TICKS); }
    public static RedstoneSignalCardMode getMode(ItemStack stack) { return RedstoneSignalCardMode.byName(stack.getOrCreateTag().getString(MODE_TAG)); }
    public static void setMode(ItemStack stack, RedstoneSignalCardMode mode) { stack.getOrCreateTag().putString(MODE_TAG, mode.getSerializedName()); }
    public static int getHoldTicks(ItemStack stack) { int t = stack.getOrCreateTag().getInt(HOLD_TAG); return t <= 0 ? DEFAULT_HOLD_TICKS : clampHoldTicks(t); }
    public static void setHoldTicks(ItemStack stack, int ticks) { stack.getOrCreateTag().putInt(HOLD_TAG, clampHoldTicks(ticks)); }
    public static int resolveOutputDurationTicks(ItemStack stack) { if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof RedstoneSignalCardItem)) return DEFAULT_PULSE_DURATION_TICKS; int t=getHoldTicks(stack); return t <= MIN_HOLD_TICKS ? DEFAULT_PULSE_DURATION_TICKS : t; }
    public static int modeDisplayColor(RedstoneSignalCardMode mode) { return mode == RedstoneSignalCardMode.ORDER ? 0x55AA55 : mode == RedstoneSignalCardMode.CRAFT ? 0x4A78D0 : 0xD04A4A; }
    public static String formatIntervalText(int ticks) { ticks=clampHoldTicks(ticks); if(ticks<=60)return ticks+"tick"; if(ticks<=1200)return (ticks/20)+"秒"; return (ticks/1200)+"分"; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) { return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide()); }
    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        RedstoneSignalCardMode mode=getMode(stack); MutableComponent line=Component.translatable("item.ae2utility.redstone_signal_card.tooltip_mode_prefix").append(mode.getDisplayName().copy().withStyle(s->s.withColor(modeDisplayColor(mode)))); tooltip.add(line);
        if(mode != RedstoneSignalCardMode.UNTIL_RECIPE_COMPLETE) tooltip.add(Component.translatable("item.ae2utility.redstone_signal_card.tooltip_interval_prefix").append(Component.literal(formatIntervalText(getHoldTicks(stack))).withStyle(s->s.withColor(0xD04A4A))));
        tooltip.add(Component.translatable("item.ae2utility.redstone_signal_card.tooltip_hint").withStyle(ChatFormatting.BLUE));
    }
}
