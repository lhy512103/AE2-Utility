package com.lhy.ae2utility.item;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import appeng.items.materials.UpgradeCardItem;

import com.lhy.ae2utility.card.NbtTearFilter;
import com.lhy.ae2utility.init.ModMenus;
import com.lhy.ae2utility.menu.NbtTearCardMenu;

/**
 * Must extend {@link UpgradeCardItem} so AE2 {@code RestrictedInputSlot.UPGRADES} accepts it.
 */
public class NbtTearCardItem extends UpgradeCardItem {
    public static final String ITEMS_TAG = "Ae2UtilityNbtTearItems";

    public NbtTearCardItem(Properties properties) {
        super(properties);
    }

    public static NbtTearFilter getFilter(ItemStack stack) {
        List<ResourceLocation> ids = new ArrayList<>();
        ListTag list = stack.getOrCreateTag().getList(ITEMS_TAG, 8);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) {
                ids.add(id);
            }
        }
        return new NbtTearFilter(ids);
    }

    public static void setFilter(ItemStack stack, NbtTearFilter filter) {
        ListTag list = new ListTag();
        for (ResourceLocation id : filter.itemIds()) {
            list.add(StringTag.valueOf(id.toString()));
        }
        stack.getOrCreateTag().put(ITEMS_TAG, list);
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
            NetworkHooks.openScreen(serverPlayer, provider, buf -> buf.writeBoolean(hand == InteractionHand.MAIN_HAND));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        NbtTearFilter filter = getFilter(stack);
        if (filter.itemIds().isEmpty()) {
            tooltip.add(Component.translatable("item.ae2utility.nbt_tear_card.tooltip_all"));
        } else {
            tooltip.add(Component.translatable("item.ae2utility.nbt_tear_card.tooltip_whitelist", filter.itemIds().size()));
        }
    }
}
