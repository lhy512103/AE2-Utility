package com.lhy.ae2utility.mixin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

import com.lhy.ae2utility.integration.ae2.PatternProviderTearStacks;
import appeng.helpers.patternprovider.UnlockCraftingEvent;

import com.lhy.ae2utility.card.NbtTearCardThreadLocal;
import com.lhy.ae2utility.card.NbtTearFilter;
import com.lhy.ae2utility.debug.NbtTearCardDebug;
import com.lhy.ae2utility.init.ModDataComponents;
import com.lhy.ae2utility.integration.ae2.NbtTearLogicAccess;
import com.lhy.ae2utility.item.NbtTearCardItem;

@Mixin(PatternProviderLogic.class)
public abstract class MixinPatternProviderLogic implements NbtTearLogicAccess {

    private static final String AE2UTILITY_TEAR_CARD = "ae2utility_nbt_tear_card";

    @Shadow
    @Final
    PatternProviderLogicHost host;

    @Shadow
    private GenericStack unlockStack;

    @Shadow
    private UnlockCraftingEvent unlockEvent;

    @Unique
    private ItemStackHandler ae2utility$tearHandler;

    /**
     * 最近一次 {@code LOCK_UNTIL_RESULT} 推样板时的全部产物键（去重保序）；用于多产物样板下非主输出先进网时的解锁匹配。
     * 从 NBT 读回时仅知主输出，退化为单元素列表。
     */
    @Unique
    private final List<AEKey> ae2utility$unlockOutputWhats = new ArrayList<>();

    @Override
    public ItemStackHandler ae2utility$getTearHandler() {
        if (ae2utility$tearHandler == null) {
            ae2utility$tearHandler = new ItemStackHandler(1) {
                @Override
                protected void onContentsChanged(int slot) {
                    host.saveChanges();
                }
            };
        }
        return ae2utility$tearHandler;
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void ae2utility$writeTear(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        ItemStack card = ae2utility$getTearHandler().getStackInSlot(0);
        if (!card.isEmpty()) {
            tag.put(AE2UTILITY_TEAR_CARD, card.save(registries));
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void ae2utility$readTear(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (tag.contains(AE2UTILITY_TEAR_CARD)) {
            ItemStack.parse(registries, tag.getCompound(AE2UTILITY_TEAR_CARD))
                    .ifPresent(s -> ae2utility$getTearHandler().setStackInSlot(0, s));
        } else {
            ae2utility$getTearHandler().setStackInSlot(0, ItemStack.EMPTY);
        }
        ae2utility$rebuildUnlockOutputWhatsAfterNbt();
    }

    @Inject(method = "addDrops", at = @At("TAIL"))
    private void ae2utility$dropTear(List<ItemStack> drops, CallbackInfo ci) {
        ItemStack card = ae2utility$getTearHandler().getStackInSlot(0);
        if (!card.isEmpty()) {
            drops.add(card);
            ae2utility$getTearHandler().setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void ae2utility$clearTear(CallbackInfo ci) {
        ae2utility$getTearHandler().setStackInSlot(0, ItemStack.EMPTY);
    }

    @Inject(method = "resetCraftingLock", at = @At("TAIL"))
    private void ae2utility$clearUnlockOutputWhats(CallbackInfo ci) {
        ae2utility$unlockOutputWhats.clear();
    }

    @Inject(method = "onPushPatternSuccess", at = @At("TAIL"))
    private void ae2utility$captureUnlockOutputWhats(IPatternDetails pattern, CallbackInfo ci) {
        ae2utility$unlockOutputWhats.clear();
        if (unlockEvent != UnlockCraftingEvent.RESULT || unlockStack == null) {
            return;
        }
        var seen = new LinkedHashSet<AEKey>();
        for (var out : pattern.getOutputs()) {
            seen.add(out.what());
        }
        ae2utility$unlockOutputWhats.addAll(seen);
        if (ae2utility$unlockOutputWhats.isEmpty()) {
            ae2utility$unlockOutputWhats.add(unlockStack.what());
        }
    }

    @Inject(method = "doWork", at = @At("HEAD"))
    private void ae2utility$setThreadLocal(CallbackInfoReturnable<Boolean> cir) {
        ItemStack card = ae2utility$getEffectiveTearCardStack();
        if (!card.isEmpty() && card.getItem() instanceof NbtTearCardItem) {
            NbtTearFilter filter = card.getOrDefault(ModDataComponents.NBT_TEAR_FILTER, NbtTearFilter.DEFAULT);
            NbtTearCardThreadLocal.set(filter);
        }
    }

    @Inject(method = "doWork", at = @At("RETURN"))
    private void ae2utility$clearThreadLocal(CallbackInfoReturnable<Boolean> cir) {
        NbtTearCardThreadLocal.clear();
    }

    @Inject(method = "pushPattern", at = @At("HEAD"))
    private void ae2utility$pushPatternTearHead(IPatternDetails patternDetails, KeyCounter[] inputHolder, CallbackInfoReturnable<Boolean> cir) {
        ItemStack card = ae2utility$getEffectiveTearCardStack();
        if (!card.isEmpty() && card.getItem() instanceof NbtTearCardItem) {
            NbtTearCardThreadLocal.set(card.getOrDefault(ModDataComponents.NBT_TEAR_FILTER, NbtTearFilter.DEFAULT));
        }
    }

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void ae2utility$pushPatternTearReturn(CallbackInfoReturnable<Boolean> cir) {
        NbtTearCardThreadLocal.clear();
    }

    @Inject(method = "onStackReturnedToNetwork", at = @At("HEAD"))
    private void ae2utility$logReturnedHead(GenericStack genericStack, CallbackInfo ci) {
        if (unlockEvent != UnlockCraftingEvent.RESULT || unlockStack == null) {
            return;
        }
        if (!unlockStack.what().equals(genericStack.what())) {
            ItemStack card = ae2utility$getEffectiveTearCardStack();
            boolean empty = card.isEmpty();
            boolean hasTear = !empty && card.getItem() instanceof NbtTearCardItem;
            NbtTearCardDebug.logStackReturnedStrictMismatchHead(unlockStack, genericStack, empty, hasTear);
        }
    }

    @Unique
    private void ae2utility$rebuildUnlockOutputWhatsAfterNbt() {
        ae2utility$unlockOutputWhats.clear();
        if (unlockEvent == UnlockCraftingEvent.RESULT && unlockStack != null) {
            ae2utility$unlockOutputWhats.add(unlockStack.what());
        }
    }

    /**
     * AE2 19.2.x 字节码在 {@code onStackReturnedToNetwork} 里对 {@code what()} 的返回值走
     * {@link Object#equals}（见 {@code invokevirtual java/lang/Object.equals}），故必须重定向该调用。
     */
    @Redirect(
            method = "onStackReturnedToNetwork",
            at = @At(value = "INVOKE", target = "Ljava/lang/Object;equals(Ljava/lang/Object;)Z"))
    private boolean ae2utility$unlockStackWhatEqualsReturned(Object unlockWhat, Object returnedKey) {
        boolean vanilla = java.util.Objects.equals(unlockWhat, returnedKey);
        if (unlockEvent != UnlockCraftingEvent.RESULT || unlockStack == null) {
            return vanilla;
        }
        if (vanilla) {
            return true;
        }
        ItemStack card = ae2utility$getEffectiveTearCardStack();
        if (card.isEmpty() || !(card.getItem() instanceof NbtTearCardItem)) {
            NbtTearCardDebug.logSkipNoTearCard(unlockWhat, returnedKey, card.isEmpty());
            return false;
        }
        NbtTearFilter filter = card.getOrDefault(ModDataComponents.NBT_TEAR_FILTER, NbtTearFilter.DEFAULT);
        String filterSummary = filter.itemIds().isEmpty() ? "whitelist_empty(all_items)" : ("whitelist_ids=" + filter.itemIds().size());
        if (!(returnedKey instanceof AEKey rk)) {
            NbtTearCardDebug.logUnlockCompare(unlockWhat, returnedKey, false, false, true, filterSummary + "_non_aekey");
            return false;
        }
        boolean fuzzy = false;
        for (AEKey candidate : ae2utility$unlockOutputWhats) {
            if (NbtTearFilter.matchesUnlockExpected(candidate, rk, filter)) {
                fuzzy = true;
                break;
            }
        }
        if (!fuzzy && unlockWhat instanceof AEKey uk) {
            fuzzy = NbtTearFilter.matchesUnlockExpected(uk, rk, filter);
        }
        NbtTearCardDebug.logUnlockCompare(unlockWhat, returnedKey, false, fuzzy, true, filterSummary + "_multiOut=" + ae2utility$unlockOutputWhats.size());
        return fuzzy;
    }
}
