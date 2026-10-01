package com.lhy.ae2utility.item;
import java.util.List; import javax.annotation.Nullable;
import com.lhy.ae2utility.card.NbtTearFilter; import appeng.items.materials.UpgradeCardItem;
import net.minecraft.network.chat.Component; import net.minecraft.world.InteractionHand; import net.minecraft.world.InteractionResultHolder; import net.minecraft.world.entity.player.Player; import net.minecraft.world.item.ItemStack; import net.minecraft.world.item.TooltipFlag; import net.minecraft.world.level.Level;
/** Upgrade card that permits item-id matching while ignoring NBT. */
public class NbtTearCardItem extends UpgradeCardItem {
 public static final String ITEMS_TAG="Ae2UtilityNbtTearItems";
 public NbtTearCardItem(Properties p){super(p);}
 public static NbtTearFilter getFilter(ItemStack s){ List<net.minecraft.resources.ResourceLocation> ids=new java.util.ArrayList<>(); net.minecraft.nbt.ListTag list=s.getOrCreateTag().getList(ITEMS_TAG,8); for(int i=0;i<list.size();i++)ids.add(new net.minecraft.resources.ResourceLocation(list.getString(i))); return new NbtTearFilter(ids); }
 public static void setFilter(ItemStack s,NbtTearFilter f){net.minecraft.nbt.ListTag list=new net.minecraft.nbt.ListTag(); for(var id:f.itemIds())list.add(net.minecraft.nbt.StringTag.valueOf(id.toString())); s.getOrCreateTag().put(ITEMS_TAG,list);}
 @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide());}
 @Override public void appendHoverText(ItemStack s,@Nullable Level level,List<Component> t,TooltipFlag f){super.appendHoverText(s,level,t,f); NbtTearFilter x=getFilter(s); t.add(Component.translatable(x.itemIds().isEmpty()?"item.ae2utility.nbt_tear_card.tooltip_all":"item.ae2utility.nbt_tear_card.tooltip_whitelist",x.itemIds().size()));}
}
