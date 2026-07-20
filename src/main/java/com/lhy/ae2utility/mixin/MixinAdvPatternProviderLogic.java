package com.lhy.ae2utility.mixin;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

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
import appeng.helpers.patternprovider.UnlockCraftingEvent;

import com.lhy.ae2utility.card.NbtTearCardThreadLocal;
import com.lhy.ae2utility.card.NbtTearFilter;
import com.lhy.ae2utility.card.RedstoneSignalCardMode;
import com.lhy.ae2utility.debug.Ae2UtilityRedstoneSignalDebugLog;
import com.lhy.ae2utility.debug.NbtTearCardDebug;
import com.lhy.ae2utility.init.ModDataComponents;
import com.lhy.ae2utility.integration.ae2.NbtTearLogicAccess;
import com.lhy.ae2utility.integration.ae2.PatternProviderSignalAccess;
import com.lhy.ae2utility.item.NbtTearCardItem;

@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic", remap = false)
public abstract class MixinAdvPatternProviderLogic implements NbtTearLogicAccess, PatternProviderSignalAccess {

    private static final String AE2UTILITY_TEAR_CARD = "ae2utility_nbt_tear_card";
    private static final String AE2UTILITY_PENDING_OUTPUTS = "ae2utility_pending_outputs";

    @Shadow
    private GenericStack unlockStack;

    @Shadow
    private UnlockCraftingEvent unlockEvent;

    @Unique
    private ItemStackHandler ae2utility$tearHandler;

    @Unique
    private final List<AEKey> ae2utility$unlockOutputWhats = new ArrayList<>();

    @Unique
    private final List<GenericStack> ae2utility$pendingOutputReturns = new ArrayList<>();

    @Unique
    private int ae2utility$signalPulseUntilTick;

    @Unique
    private boolean ae2utility$continuousSignalActive;

    @Unique
    private boolean ae2utility$pendingCraftReturnRedstonePulse;

    @Unique
    private boolean ae2utility$lastRedstoneActive;

    @Unique
    private Object ae2utility$cachedHost;

    @Shadow
    public abstract boolean isBusy();

    @Shadow
    public abstract appeng.helpers.patternprovider.PatternProviderReturnInventory getReturnInv();

    @Override
    public ItemStackHandler ae2utility$getTearHandler() {
        if (ae2utility$tearHandler == null) {
            ae2utility$tearHandler = new ItemStackHandler(1);
        }
        return ae2utility$tearHandler;
    }

    @Override
    public int ae2utility$getSignalPulseUntilTick() {
        return ae2utility$signalPulseUntilTick;
    }

    @Override
    public void ae2utility$setSignalPulseUntilTick(int gameTime) {
        ae2utility$signalPulseUntilTick = gameTime;
    }

    @Override
    public boolean ae2utility$isContinuousSignalActive() {
        return ae2utility$continuousSignalActive;
    }

    @Override
    public void ae2utility$setContinuousSignalActive(boolean active) {
        ae2utility$continuousSignalActive = active;
    }

    @Override
    public boolean ae2utility$getLastRedstoneActive() {
        return ae2utility$lastRedstoneActive;
    }

    @Override
    public void ae2utility$setLastRedstoneActive(boolean active) {
        ae2utility$lastRedstoneActive = active;
    }

    /** 反射缓存 Advanced AE 的 host（其类型编译期不可见）；交接口 host 版统一触发，替代原先重复三套反射。 */
    @Unique
    private Object ae2utility$host() {
        if (ae2utility$cachedHost == null) {
            try {
                java.lang.reflect.Field f = this.getClass().getDeclaredField("host");
                f.setAccessible(true);
                ae2utility$cachedHost = f.get(this);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return ae2utility$cachedHost;
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void ae2utility$writeTear(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        ItemStack card = ae2utility$getTearHandler().getStackInSlot(0);
        if (!card.isEmpty()) {
            tag.put(AE2UTILITY_TEAR_CARD, card.save(registries));
        }
        if (ae2utility$signalPulseUntilTick > 0) {
            tag.putInt("ae2utility_signal_pulse_until", ae2utility$signalPulseUntilTick);
        }
        if (ae2utility$continuousSignalActive) {
            tag.putBoolean("ae2utility_continuous_signal_active", true);
        }
        if (!ae2utility$pendingOutputReturns.isEmpty()) {
            var list = new net.minecraft.nbt.ListTag();
            for (var stack : ae2utility$pendingOutputReturns) {
                list.add(GenericStack.writeTag(registries, stack));
            }
            tag.put(AE2UTILITY_PENDING_OUTPUTS, list);
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
        ae2utility$signalPulseUntilTick = tag.getInt("ae2utility_signal_pulse_until");
        ae2utility$continuousSignalActive = tag.getBoolean("ae2utility_continuous_signal_active");
        ae2utility$rebuildUnlockOutputWhatsAfterNbt();
        ae2utility$pendingOutputReturns.clear();
        if (tag.contains(AE2UTILITY_PENDING_OUTPUTS, net.minecraft.nbt.Tag.TAG_LIST)) {
            var list = tag.getList(AE2UTILITY_PENDING_OUTPUTS, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                var stack = GenericStack.readTag(registries, list.getCompound(i));
                if (stack != null && stack.amount() > 0) {
                    ae2utility$pendingOutputReturns.add(stack);
                }
            }
        }
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
        ae2utility$unlockOutputWhats.clear();
        ae2utility$signalPulseUntilTick = 0;
        ae2utility$continuousSignalActive = false;
        ae2utility$lastRedstoneActive = false;
        ae2utility$pendingCraftReturnRedstonePulse = false;
        ae2utility$pendingOutputReturns.clear();
    }

    @Inject(method = "resetCraftingLock", at = @At("TAIL"))
    private void ae2utility$clearUnlockOutputWhats(CallbackInfo ci) {
        ae2utility$unlockOutputWhats.clear();
        ae2utility$pendingOutputReturns.clear();
    }

    @Inject(method = "onPushPatternSuccess", at = @At("TAIL"))
    private void ae2utility$captureUnlockOutputWhats(IPatternDetails pattern, CallbackInfo ci) {
        ae2utility$unlockOutputWhats.clear();
        if (pattern == null) {
            ae2utility$pendingOutputReturns.clear();
            return;
        }
        var seen = new LinkedHashSet<AEKey>();
        for (var out : pattern.getOutputs()) {
            seen.add(out.what());
        }
        if (unlockEvent == UnlockCraftingEvent.RESULT && unlockStack != null) {
            seen.add(unlockStack.what());
        }
        ae2utility$unlockOutputWhats.addAll(seen);
        ae2utility$rebuildPendingOutputs(pattern);
    }

    /** 状态兜底：跟踪非瞬时派发的忙碌/返还状态，主要负责 UNTIL 的下降沿。 */
    @Inject(method = "sendStacksOut", at = @At("RETURN"))
    private void ae2utility$driveRedstoneStateMachine(CallbackInfoReturnable<Boolean> cir) {
        ae2utility$tickRedstoneStateMachine(ae2utility$host(), isBusy(), !getReturnInv().isEmpty(), false);
    }

    @Inject(method = "doWork", at = @At("HEAD"))
    private void ae2utility$setThreadLocal(CallbackInfoReturnable<Boolean> cir) {
        ItemStack card = ae2utility$getEffectiveTearCardStack();
        if (!card.isEmpty() && card.getItem() instanceof NbtTearCardItem) {
            NbtTearCardThreadLocal.set(card.getOrDefault(ModDataComponents.NBT_TEAR_FILTER, NbtTearFilter.DEFAULT));
        }
    }

    @Inject(method = "doWork", at = @At("RETURN"))
    private void ae2utility$clearThreadLocal(CallbackInfoReturnable<Boolean> cir) {
        NbtTearCardThreadLocal.clear();
    }

    @Inject(method = "pushPattern", at = @At("HEAD"))
    private void ae2utility$pushPatternTearHead(IPatternDetails patternDetails, KeyCounter[] inputHolder,
        CallbackInfoReturnable<Boolean> cir) {
        if (Ae2UtilityRedstoneSignalDebugLog.PULSE_TRACE) {
            ItemStack diagnosticCard = ae2utility$getRedstoneSignalCardStack();
            Ae2UtilityRedstoneSignalDebugLog.pulse(
                    "adv_push_pattern_head logic={} pattern={} busy={} returnPending={} card={} mode={}",
                    this.getClass().getName(),
                    patternDetails == null ? "null" : patternDetails.getClass().getName(),
                    isBusy(), !getReturnInv().isEmpty(),
                    diagnosticCard.isEmpty() ? "missing" : diagnosticCard.getItem(),
                    diagnosticCard.isEmpty() ? "none" : diagnosticCard.getOrDefault(ModDataComponents.REDSTONE_SIGNAL_CARD_MODE,
                            RedstoneSignalCardMode.ORDER));
        }
        ItemStack card = ae2utility$getEffectiveTearCardStack();
        if (!card.isEmpty() && card.getItem() instanceof NbtTearCardItem) {
            NbtTearCardThreadLocal.set(card.getOrDefault(ModDataComponents.NBT_TEAR_FILTER, NbtTearFilter.DEFAULT));
        }
    }

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void ae2utility$pushPatternTearReturn(CallbackInfoReturnable<Boolean> cir) {
        NbtTearCardThreadLocal.clear();
        if (Ae2UtilityRedstoneSignalDebugLog.PULSE_TRACE) {
            Ae2UtilityRedstoneSignalDebugLog.pulse(
                    "adv_push_pattern_return logic={} accepted={} busy={} returnPending={}",
                    this.getClass().getName(), cir.getReturnValueZ(), isBusy(), !getReturnInv().isEmpty());
        }
        if (!cir.getReturnValueZ()) {
            return;
        }
        ae2utility$onSuccessfulPatternPush(ae2utility$host(), isBusy(), !getReturnInv().isEmpty());
    }

    @Inject(method = "onStackReturnedToNetwork", at = @At("HEAD"))
    private void ae2utility$prepareCraftReturnPulseAndLogTear(GenericStack genericStack, CallbackInfo ci) {
        boolean craftMode = ae2utility$shouldEmitFor(RedstoneSignalCardMode.CRAFT);
        boolean waitingResultUnlock = unlockEvent == UnlockCraftingEvent.RESULT;
        boolean matchedLastPushOutputs = ae2utility$consumeReturnedOutput(genericStack);
        boolean recipeComplete = genericStack != null
                && (waitingResultUnlock || matchedLastPushOutputs)
                && ae2utility$pendingOutputReturns.isEmpty();
        if (recipeComplete && ae2utility$isUntilRecipeMode()) {
            Ae2UtilityRedstoneSignalDebugLog.pulse("adv_until_recipe_complete logic={} stack={}",
                    this.getClass().getName(), genericStack);
            ae2utility$disableContinuousSignalFromHost(ae2utility$host());
        }
        ae2utility$pendingCraftReturnRedstonePulse = recipeComplete && craftMode;
        if (Ae2UtilityRedstoneSignalDebugLog.PULSE_TRACE) {
            Ae2UtilityRedstoneSignalDebugLog.pulse(
                    "adv_return_inventory_cb logic={} unlockEvent={} craftMode={} pendingPulse={} "
                            + "waitingResultUnlock={} matchedPushOutputs={} remainingOutputs={} stackBrief={}",
                    this.getClass().getName(),
                    unlockEvent != null ? unlockEvent.name() : "null",
                    craftMode,
                    ae2utility$pendingCraftReturnRedstonePulse,
                    waitingResultUnlock,
                    matchedLastPushOutputs,
                    ae2utility$pendingOutputReturns.size(),
                    genericStack != null ? genericStack.toString() : "null");
        }
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

    @Inject(method = "onStackReturnedToNetwork", at = @At(value = "RETURN"))
    private void ae2utility$emitCraftPulseOnReturnedExit(GenericStack genericStack, CallbackInfo ci) {
        if (!ae2utility$pendingCraftReturnRedstonePulse) {
            return;
        }
        ae2utility$pendingCraftReturnRedstonePulse = false;
        Ae2UtilityRedstoneSignalDebugLog.pulse("adv_craft_return_emit logic={} stack={}",
                this.getClass().getName(), genericStack);
        ae2utility$triggerSignalPulseFromHost(ae2utility$host());
    }

    @Unique
    private boolean ae2utility$consumeReturnedOutput(GenericStack genericStack) {
        if (genericStack == null || ae2utility$pendingOutputReturns.isEmpty()) {
            return false;
        }
        for (int i = 0; i < ae2utility$pendingOutputReturns.size(); i++) {
            var expected = ae2utility$pendingOutputReturns.get(i);
            if (!ae2utility$outputMatches(expected.what(), genericStack.what())) {
                continue;
            }
            long remaining = expected.amount() - genericStack.amount();
            if (remaining > 0) {
                ae2utility$pendingOutputReturns.set(i, new GenericStack(expected.what(), remaining));
            } else {
                ae2utility$pendingOutputReturns.remove(i);
            }
            if (Ae2UtilityRedstoneSignalDebugLog.PULSE_TRACE) {
                Ae2UtilityRedstoneSignalDebugLog.pulse(
                        "adv_pending_output_consume logic={} matched={} returnedAmount={} remainingAmount={} remainingEntries={}",
                        this.getClass().getName(),
                        expected.what(),
                        genericStack.amount(),
                        Math.max(remaining, 0),
                        ae2utility$pendingOutputReturns.size());
            }
            return true;
        }
        return false;
    }

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
        NbtTearCardDebug.logUnlockCompare(unlockWhat, returnedKey, false, fuzzy, true,
                filterSummary + "_multiOut=" + ae2utility$unlockOutputWhats.size());
        return fuzzy;
    }

    @Unique
    private void ae2utility$rebuildUnlockOutputWhatsAfterNbt() {
        ae2utility$unlockOutputWhats.clear();
        if (unlockEvent == UnlockCraftingEvent.RESULT && unlockStack != null) {
            ae2utility$unlockOutputWhats.add(unlockStack.what());
        }
    }

    @Unique
    private void ae2utility$rebuildPendingOutputs(IPatternDetails pattern) {
        if (pattern == null) {
            return;
        }
        boolean addedAny = false;
        for (var out : pattern.getOutputs()) {
            ae2utility$mergePendingOutput(out.what(), out.amount());
            addedAny = true;
        }
        if (!addedAny && unlockEvent == UnlockCraftingEvent.RESULT && unlockStack != null) {
            ae2utility$mergePendingOutput(unlockStack.what(), unlockStack.amount());
        }
        if (Ae2UtilityRedstoneSignalDebugLog.PULSE_TRACE) {
            Ae2UtilityRedstoneSignalDebugLog.pulse(
                    "adv_pending_output_rebuild logic={} unlockEvent={} outputs={}",
                    this.getClass().getName(),
                    unlockEvent != null ? unlockEvent.name() : "null",
                    ae2utility$pendingOutputReturns);
        }
    }

    @Unique
    private void ae2utility$mergePendingOutput(AEKey what, long amount) {
        if (what == null || amount <= 0) {
            return;
        }
        for (int i = 0; i < ae2utility$pendingOutputReturns.size(); i++) {
            var existing = ae2utility$pendingOutputReturns.get(i);
            if (ae2utility$outputMatches(existing.what(), what)) {
                ae2utility$pendingOutputReturns.set(i, new GenericStack(existing.what(), existing.amount() + amount));
                return;
            }
        }
        ae2utility$pendingOutputReturns.add(new GenericStack(what, amount));
    }

    @Unique
    private boolean ae2utility$outputMatches(AEKey expected, AEKey returned) {
        if (expected == null || returned == null) {
            return false;
        }
        ItemStack card = ae2utility$getEffectiveTearCardStack();
        if (!card.isEmpty() && card.getItem() instanceof NbtTearCardItem) {
            NbtTearFilter filter = card.getOrDefault(ModDataComponents.NBT_TEAR_FILTER, NbtTearFilter.DEFAULT);
            return NbtTearFilter.matchesUnlockExpected(expected, returned, filter);
        }
        return expected.equals(returned);
    }
}
