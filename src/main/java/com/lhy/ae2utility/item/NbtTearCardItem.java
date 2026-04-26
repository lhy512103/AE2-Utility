package com.lhy.ae2utility.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import appeng.items.materials.UpgradeCardItem;

import com.lhy.ae2utility.card.NbtTearFilter;
import com.lhy.ae2utility.init.ModDataComponents;
import com.lhy.ae2utility.init.ModMenus;
import com.lhy.ae2utility.menu.NbtTearCardMenu;

/**
 * 必须继承 AE2 的 {@link UpgradeCardItem}，否则 {@link appeng.menu.slot.RestrictedInputSlot} 的
 * {@code UPGRADES} 类型会因 {@link appeng.api.upgrades.Upgrades#isUpgradeCardItem} 为 false 而拒绝放入。
 */
public class NbtTearCardItem extends UpgradeCardItem {
    public NbtTearCardItem(Properties properties) {
        super(properties.component(ModDataComponents.NBT_TEAR_FILTER, NbtTearFilter.DEFAULT));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() != this) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            MenuProvider provider = new SimpleMenuProvider(
                    (id, inv, p) -> new NbtTearCardMenu(ModMenus.NBT_TEAR_CARD.get(), id, inv, hand),
                    Component.translatable("gui.ae2utility.nbt_tear_card"));
            serverPlayer.openMenu(provider, buf -> buf.writeBoolean(hand == InteractionHand.MAIN_HAND));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        NbtTearFilter filter = stack.get(ModDataComponents.NBT_TEAR_FILTER);
        if (filter == null || filter.itemIds().isEmpty()) {
            tooltipComponents.add(Component.translatable("item.ae2utility.nbt_tear_card.tooltip_all"));
        } else {
            tooltipComponents.add(Component.translatable("item.ae2utility.nbt_tear_card.tooltip_whitelist", filter.itemIds().size()));
        }
    }
}
