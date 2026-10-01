package com.lhy.ae2utility.util;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
public final class MeIngredientExtraction {
 private MeIngredientExtraction() {}
 public static Ingredient ingredientFromItemStacks(List<ItemStack> stacks){return Ingredient.of(stacks.stream().map(ItemStack::copy));}
 public static List<ItemStack> extractAlternatives(MEStorage storage,IEnergySource energy,IActionSource source,Object filter,List<ItemStack> alternatives,int amount){List<ItemStack> out=new ArrayList<>();int left=amount;for(ItemStack s:alternatives){if(left<=0)break;AEItemKey k=AEItemKey.of(s);if(k==null)continue;long n=StorageHelper.poweredExtraction(energy,storage,k,left,source);if(n>0){ItemStack got=s.copyWithCount((int)Math.min(Integer.MAX_VALUE,n));out.add(got);left-=got.getCount();}}return out;}
 public static boolean reserveOneUnit(Map<AEItemKey,Long> available,List<ItemStack> alternatives,Ingredient ingredient){for(ItemStack s:alternatives){AEItemKey k=AEItemKey.of(s);if(k!=null&&available.getOrDefault(k,0L)>0){available.computeIfPresent(k,(x,v)->v-1);return true;}}return false;}
}
