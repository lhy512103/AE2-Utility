package com.lhy.ae2utility.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.tooltip.TooltipLocation;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import appeng.api.features.IGridLinkableHandler;
import appeng.api.ids.AEComponents;

import com.lhy.ae2utility.service.MeQuickTransferService;

public class MeQuickTransferToolItem extends Item {
    public static final IGridLinkableHandler LINKABLE_HANDLER = new IGridLinkableHandler() {
        @Override
        public boolean canLink(ItemStack stack) {
            return stack.getItem() instanceof MeQuickTransferToolItem;
        }

        @Override
        public void link(ItemStack stack, GlobalPos pos) {
            stack.set(AEComponents.WIRELESS_LINK_TARGET, pos);
        }

        @Override
        public void unlink(ItemStack stack) {
            stack.remove(AEComponents.WIRELESS_LINK_TARGET);
        }
    };

    public MeQuickTransferToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }

        MeQuickTransferService.start(player, stack, context.getClickedPos(), context.getClickedFace(),
                player.isShiftKeyDown());
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            MeQuickTransferService.stop(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    public static void registerTooltipAppenders(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, (stack, context, display, player, flag, builder) -> {
            if (stack.getItem() instanceof MeQuickTransferToolItem) {
                appendToolTooltip(stack, builder);
            }
        });
    }

    private static void appendToolTooltip(ItemStack stack, Consumer<Component> tooltipComponents) {
        if (stack.get(AEComponents.WIRELESS_LINK_TARGET) == null) {
            tooltipComponents.accept(Component.translatable("item.ae2utility.me_quick_transfer.tooltip_unlinked")
                    .withStyle(ChatFormatting.RED));
        } else {
            tooltipComponents.accept(Component.translatable("item.ae2utility.me_quick_transfer.tooltip_linked")
                    .withStyle(ChatFormatting.GREEN));
        }
        tooltipComponents.accept(Component.translatable("item.ae2utility.me_quick_transfer.tooltip_group")
                .withStyle(ChatFormatting.AQUA));
        tooltipComponents.accept(Component.translatable("item.ae2utility.me_quick_transfer.tooltip_all")
                .withStyle(ChatFormatting.GOLD));
        tooltipComponents.accept(Component.translatable("item.ae2utility.me_quick_transfer.tooltip_stop")
                .withStyle(ChatFormatting.RED));
    }
}
