package com.lhy.ae2utility.card;
import java.util.ArrayList; import java.util.List;
import appeng.api.config.FuzzyMode; import appeng.api.stacks.AEItemKey; import appeng.api.stacks.AEKey;
import net.minecraft.core.Registry; import net.minecraft.core.registries.BuiltInRegistries; import net.minecraft.resources.ResourceLocation; import net.minecraft.world.item.ItemStack;
/** NBT tear whitelist; empty means every item. */
public record NbtTearFilter(List<ResourceLocation> itemIds) {
 public static final NbtTearFilter DEFAULT = new NbtTearFilter(List.of());
 public NbtTearFilter { itemIds = itemIds == null ? List.of() : List.copyOf(itemIds); }
 public static boolean matchesUnlockExpected(AEKey expected, AEKey returned, NbtTearFilter f) {
  if(expected==null||returned==null)return false; if(expected.equals(returned))return true;
  if(expected instanceof AEItemKey a && returned instanceof AEItemKey b && a.getItem()==b.getItem()) return f==null||f.itemIds().isEmpty()||f.itemIds().contains(expected.getId());
  return expected.getClass()==returned.getClass() && expected.fuzzyEquals(returned,FuzzyMode.IGNORE_ALL) && (f==null||f.itemIds().isEmpty()||f.itemIds().contains(expected.getId()));
 }
 public static NbtTearFilter fromItemStacks(List<ItemStack> stacks){ List<ResourceLocation> ids=new ArrayList<>(); for(ItemStack s:stacks)if(s!=null&&!s.isEmpty()){ResourceLocation id=BuiltInRegistries.ITEM.getKey(s.getItem());if(!ids.contains(id))ids.add(id);} return new NbtTearFilter(ids); }
}
