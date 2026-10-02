package com.lhy.ae2utility.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.lhy.ae2utility.jei.JeiBookmarkKeysCache;
import com.lhy.ae2utility.jei.StochasticOutputDetector;
import com.lhy.ae2utility.util.GenericIngredientUtil;
import com.lhy.ae2utility.util.ItemIds;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fluids.FluidStack;

/**
 * 1.20.1 forge 精简版：从 JEI {@link IRecipeSlotsView} 构建 {@link GenericStack} 列表，
 * 供 {@link com.lhy.ae2utility.jei.JeiEncodePacketFactory} 与高亮预览共用。
 *
 * <p>1.20.1 中 JEI 的 {@code recipe} 对象是 {@link Recipe} 本身（无 {@code RecipeHolder} 包装），
 * {@link Recipe#getId()} 直接返回 {@link net.minecraft.resources.ResourceLocation}。</p>
 */
public final class RecipeTransferPacketHelper {
    private RecipeTransferPacketHelper() {
    }

    public static List<PullRecipeInputsPacket.RequestedIngredient> readRequestedIngredients(net.minecraft.network.FriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 64) return List.of();
        List<PullRecipeInputsPacket.RequestedIngredient> result = new ArrayList<>(); long total = 0;
        for (int i=0;i<size;i++) { int n=buffer.readVarInt(); if(n<0 || n>256) return List.of(); total+=n; if(total>4096)return List.of(); List<ItemStack> stacks=new ArrayList<>(n); for(int j=0;j<n;j++) stacks.add(buffer.readItem()); result.add(new PullRecipeInputsPacket.RequestedIngredient(stacks,buffer.readVarInt())); }
        return result;
    }
    public static boolean canEncodeRequestedIngredients(List<PullRecipeInputsPacket.RequestedIngredient> ingredients) { if(ingredients.size()>64)return false; long total=0; for(var i:ingredients){if(i.alternatives().size()>256)return false; total+=i.alternatives().size(); if(total>4096)return false;} return true; }
    public static void writeRequestedIngredients(net.minecraft.network.FriendlyByteBuf buffer,List<PullRecipeInputsPacket.RequestedIngredient> ingredients){if(!canEncodeRequestedIngredients(ingredients)){buffer.writeVarInt(0);return;} buffer.writeVarInt(ingredients.size()); for(var i:ingredients){buffer.writeVarInt(i.alternatives().size());for(ItemStack s:i.alternatives())buffer.writeItem(s);buffer.writeVarInt(i.count());}}
    public static String requestSignature(List<PullRecipeInputsPacket.RequestedIngredient> ingredients){return ingredients.stream().map(i->i.count()+"#"+i.alternatives().stream().map(s->stackHash(s)+"#"+s.getCount()+"#"+s.getItem()).sorted().reduce((a,b)->a+"|"+b).orElse("")).sorted().reduce((a,b)->a+"||"+b).orElse("");}
    private static long stackHash(ItemStack stack) { long hash = 31L * ItemIds.of(stack).hashCode(); var tag=stack.getTag(); return 31L * hash + (tag != null ? tag.hashCode() : 0L); }

    public static List<List<GenericStack>> getGenericStacks(IRecipeSlotsView slotsView, RecipeIngredientRole role) {
        return getGenericStacks(slotsView, role, Set.of());
    }

    public static List<List<GenericStack>> getGenericStacks(IRecipeSlotsView slotsView, RecipeIngredientRole role,
            Set<AEKey> excludedBookmarkKeys) {
        List<AEKey> bookmarkedKeys = role == RecipeIngredientRole.INPUT
                ? bookmarkKeysExcluding(excludedBookmarkKeys)
                : List.of();
        List<List<GenericStack>> slots = new ArrayList<>();

        for (IRecipeSlotView slotView : slotsView.getSlotViews(role)) {
            List<GenericStack> alternatives = collectEncodeAlternativesForInputSlot(slotView, bookmarkedKeys);
            if (alternatives.isEmpty()) {
                slots.add(isEffectivelyEmptySlot(slotView) ? List.of() : null);
            } else {
                slots.add(alternatives);
            }
        }
        return slots;
    }

    public static List<GenericStack> collectEncodeAlternativesForInputSlot(IRecipeSlotView slotView) {
        return collectEncodeAlternativesForInputSlot(slotView, JeiBookmarkKeysCache.getBookmarkKeys());
    }

    private static List<AEKey> bookmarkKeysExcluding(Set<AEKey> excluded) {
        List<AEKey> keys = JeiBookmarkKeysCache.getBookmarkKeys();
        if (excluded.isEmpty() || keys.isEmpty()) {
            return keys;
        }
        return keys.stream().filter(key -> !excluded.contains(key)).toList();
    }

    // A bookmarked alternative, if any, becomes the slot's only candidate.
    private static List<GenericStack> collectEncodeAlternativesForInputSlot(IRecipeSlotView slotView,
            List<AEKey> bookmarkedKeys) {
        List<GenericStack> alternatives = new ArrayList<>();
        long count = resolveEncodeSlotDisplayedCount(slotView);
        List<ITypedIngredient<?>> ingredients = slotView.getAllIngredients().toList();

        if (!bookmarkedKeys.isEmpty()) {
            for (AEKey bookmarkedKey : bookmarkedKeys) {
                for (ITypedIngredient<?> typed : ingredients) {
                    Object ing = typed.getIngredient();
                    AEKey ingKey = GenericIngredientUtil.toAEKey(ing);
                    if (bookmarkedKey.equals(ingKey)) {
                        return List.of(new GenericStack(ingKey, GenericIngredientUtil.resolveAmount(ing, count)));
                    }
                }
            }
        }

        for (ITypedIngredient<?> typed : ingredients) {
            Object ing = typed.getIngredient();
            AEKey ingKey = GenericIngredientUtil.toAEKey(ing);
            if (ingKey != null) {
                boolean alreadyAdded = false;
                for (GenericStack existing : alternatives) {
                    if (existing.what().equals(ingKey)) {
                        alreadyAdded = true;
                        break;
                    }
                }
                if (!alreadyAdded) {
                    alternatives.add(new GenericStack(ingKey, GenericIngredientUtil.resolveAmount(ing, count)));
                }
            }
        }

        return alternatives;
    }

    public static @Nullable GenericStack genericStackForCraftableHighlightInputSlot(IRecipeSlotView slotView) {
        long count = resolveEncodeSlotDisplayedCount(slotView);
        List<ITypedIngredient<?>> allIngredients = slotView.getAllIngredients().toList();

        ITypedIngredient<?> typed = slotView.getDisplayedIngredient().orElseGet(() -> allIngredients.isEmpty() ? null : allIngredients.get(0));
        if (typed == null) {
            return null;
        }
        return GenericIngredientUtil.toGenericStack(typed.getIngredient(), count);
    }

    public static long resolveEncodeSlotDisplayedCount(IRecipeSlotView slotView, List<ITypedIngredient<?>> allIngredients) {
        long count = 1;
        List<ItemStack> itemStacks = slotView.getIngredients(mezz.jei.api.constants.VanillaTypes.ITEM_STACK).toList();
        for (ItemStack stack : itemStacks) {
            if (!stack.isEmpty() && stack.getCount() > 1) {
                count = stack.getCount();
                break;
            }
        }
        if (count == 1) {
            List<FluidStack> fluidStacks = slotView.getIngredients(mezz.jei.api.forge.ForgeTypes.FLUID_STACK).toList();
            for (FluidStack stack : fluidStacks) {
                if (!stack.isEmpty() && stack.getAmount() > 1) {
                    count = stack.getAmount();
                    break;
                }
            }
        }
        if (count == 1) {
            for (ITypedIngredient<?> typed : allIngredients) {
                long chemicalAmount = GenericIngredientUtil.tryGetMekanismChemicalAmount(typed.getIngredient());
                if (chemicalAmount > 1) {
                    count = chemicalAmount;
                    break;
                }
            }
        }
        return count;
    }

    public static List<GenericStack> getEncodingOutputs(Object recipe, IRecipeSlotsView slotsView) {
        List<GenericStack> outputs = new ArrayList<>();
        for (IRecipeSlotView slotView : slotsView.getSlotViews(RecipeIngredientRole.OUTPUT)) {
            boolean hasPrimaryOutput = outputs.stream().anyMatch(java.util.Objects::nonNull);
            if (hasPrimaryOutput && StochasticOutputDetector.isChanceOutput(recipe, slotView)) {
                continue;
            }
            List<List<GenericStack>> singleSlot = getGenericStacks(new SingleRoleSlotsView(slotView), RecipeIngredientRole.OUTPUT);
            if (!singleSlot.isEmpty()) {
                List<GenericStack> list = singleSlot.get(0);
                if (list == null) {
                    outputs.add(null);
                } else if (!list.isEmpty()) {
                    outputs.add(list.get(0));
                }
            }
        }
        return outputs;
    }

    public static List<GenericStack> getEncodingOutputs(IRecipeSlotsView slotsView) {
        return getEncodingOutputs(null, slotsView);
    }

    private static boolean isEffectivelyEmptySlot(IRecipeSlotView slotView) {
        if (slotView.isEmpty()) {
            return true;
        }
        for (ITypedIngredient<?> typed : slotView.getAllIngredients().toList()) {
            Object ingredient = typed.getIngredient();
            if (ingredient instanceof ItemStack stack && stack.isEmpty()) {
                continue;
            }
            if (ingredient instanceof FluidStack fluid && fluid.isEmpty()) {
                continue;
            }
            if (GenericIngredientUtil.isMekanismChemicalStack(ingredient)
                    && GenericIngredientUtil.isEmptyMekanismChemicalStack(ingredient)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static long resolveEncodeSlotDisplayedCount(IRecipeSlotView slotView) {
        return resolveEncodeSlotDisplayedCount(slotView, slotView.getAllIngredients().toList());
    }

    private record SingleRoleSlotsView(IRecipeSlotView slotView) implements IRecipeSlotsView {
        @Override
        public List<IRecipeSlotView> getSlotViews() {
            return List.of(slotView);
        }

        @Override
        public List<IRecipeSlotView> getSlotViews(RecipeIngredientRole role) {
            return slotView.getRole() == role ? List.of(slotView) : List.of();
        }
    }
}
