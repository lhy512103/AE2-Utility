package com.lhy.ae2utility.item;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import appeng.api.features.IGridLinkableHandler;

import com.lhy.ae2utility.service.MeQuickTransferService;

public class MeQuickTransferToolItem extends Item {
    // Same key as AE2 wireless terminals.
    private static final String TAG_ACCESS_POINT_POS = "accessPoint";

    public static final IGridLinkableHandler LINKABLE_HANDLER = new IGridLinkableHandler() {
        @Override
        public boolean canLink(ItemStack stack) {
            return stack.getItem() instanceof MeQuickTransferToolItem;
        }

        @Override
        public void link(ItemStack stack, GlobalPos pos) {
            GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, pos)
                    .result()
                    .ifPresent(tag -> stack.getOrCreateTag().put(TAG_ACCESS_POINT_POS, tag));
        }

        @Override
        public void unlink(ItemStack stack) {
            stack.removeTagKey(TAG_ACCESS_POINT_POS);
        }
    };

    public MeQuickTransferToolItem(Properties properties) {
        super(properties);
    }

    public static @Nullable GlobalPos getLinkedPosition(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_ACCESS_POINT_POS, Tag.TAG_COMPOUND)) {
            return null;
        }
        return GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_ACCESS_POINT_POS)).result().orElse(null);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
        }

        MeQuickTransferService.start(player, stack, context.getClickedPos(), context.getClickedFace(),
                player.isShiftKeyDown());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            MeQuickTransferService.stop(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        if (getLinkedPosition(stack) == null) {
            tooltipComponents.add(Component.translatable("item.ae2utility.me_quick_transfer.tooltip_unlinked")
                    .withStyle(ChatFormatting.RED));
        } else {
            tooltipComponents.add(Component.translatable("item.ae2utility.me_quick_transfer.tooltip_linked")
                    .withStyle(ChatFormatting.GREEN));
        }
        tooltipComponents.add(Component.translatable("item.ae2utility.me_quick_transfer.tooltip_group")
                .withStyle(ChatFormatting.AQUA));
        tooltipComponents.add(Component.translatable("item.ae2utility.me_quick_transfer.tooltip_all")
                .withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.translatable("item.ae2utility.me_quick_transfer.tooltip_stop")
                .withStyle(ChatFormatting.RED));
    }
}
