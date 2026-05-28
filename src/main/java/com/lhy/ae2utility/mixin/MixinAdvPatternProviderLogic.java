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
import net.minecraft.server.level.ServerLevel;
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
    private boolean ae2utility$orderPulseDeferredUntilSendClears;

    @Unique
    private boolean ae2utility$advancedMachineBranchTriggered;

    @Shadow
    public abstract boolean isBusy();

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
        ae2utility$orderPulseDeferredUntilSendClears = false;
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

    @Inject(method = "sendStacksOut", at = @At("RETURN"))
    private void ae2utility$emitOrderPulseWhenDeferredSendListDrained(CallbackInfoReturnable<Boolean> cir) {
        if (!ae2utility$orderPulseDeferredUntilSendClears || isBusy()) {
            return;
        }
        ae2utility$orderPulseDeferredUntilSendClears = false;
        if (!ae2utility$shouldEmitFor(RedstoneSignalCardMode.ORDER) && !ae2utility$isUntilRecipeMode()) {
            return;
        }
        if (ae2utility$isUntilRecipeMode()) {
            Ae2UtilityRedstoneSignalDebugLog.pulse("adv_until_recipe_raise_after_sendlist_drained logic={}",
                    this.getClass().getName());
            ae2utility$enableContinuousSignalFromHost();
        } else {
            Ae2UtilityRedstoneSignalDebugLog.pulse("adv_order_emit_after_sendlist_drained logic={}",
                    this.getClass().getName());
            ae2utility$triggerSignalPulseFromHost();
        }
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
        ae2utility$advancedMachineBranchTriggered = false;
        ItemStack card = ae2utility$getEffectiveTearCardStack();
        if (!card.isEmpty() && card.getItem() instanceof NbtTearCardItem) {
            NbtTearCardThreadLocal.set(card.getOrDefault(ModDataComponents.NBT_TEAR_FILTER, NbtTearFilter.DEFAULT));
        }
    }

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void ae2utility$pushPatternTearReturn(CallbackInfoReturnable<Boolean> cir) {
        NbtTearCardThreadLocal.clear();
        if (!cir.getReturnValueZ()) {
            return;
        }
        boolean orderMode = ae2utility$shouldEmitFor(RedstoneSignalCardMode.ORDER);
        boolean untilRecipeMode = ae2utility$isUntilRecipeMode();
        if (!orderMode && !untilRecipeMode) {
            Ae2UtilityRedstoneSignalDebugLog.pulse("adv_order_gate_skip logic={}", this.getClass().getName());
            return;
        }
        if (ae2utility$advancedMachineBranchTriggered) {
            ae2utility$orderPulseDeferredUntilSendClears = false;
            ae2utility$advancedMachineBranchTriggered = false;
            if (untilRecipeMode) {
                Ae2UtilityRedstoneSignalDebugLog.pulse("adv_until_recipe_raise_after_machine_dispatch logic={}",
                        this.getClass().getName());
                ae2utility$enableContinuousSignalFromHost();
            } else {
                Ae2UtilityRedstoneSignalDebugLog.pulse("adv_order_emit_after_machine_dispatch logic={}",
                        this.getClass().getName());
                ae2utility$triggerSignalPulseFromHost();
            }
            return;
        }
        if (isBusy()) {
            ae2utility$orderPulseDeferredUntilSendClears = true;
            Ae2UtilityRedstoneSignalDebugLog.pulse("adv_order_emit_deferred_until_sendlist logic={}",
                    this.getClass().getName());
            return;
        }
        ae2utility$orderPulseDeferredUntilSendClears = false;
        if (untilRecipeMode) {
            Ae2UtilityRedstoneSignalDebugLog.pulse("adv_until_recipe_raise_after_dispatch logic={}",
                    this.getClass().getName());
            ae2utility$enableContinuousSignalFromHost();
        } else {
            Ae2UtilityRedstoneSignalDebugLog.pulse("adv_order_emit_after_dispatch logic={}", this.getClass().getName());
            ae2utility$triggerSignalPulseFromHost();
        }
    }

    @Inject(
            method = "pushPattern",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/implementations/blockentities/ICraftingMachine;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;Lnet/minecraft/core/Direction;)Z",
                    shift = At.Shift.AFTER))
    private void ae2utility$markCraftingMachineBranch(IPatternDetails patternDetails, KeyCounter[] inputHolder,
            CallbackInfoReturnable<Boolean> cir) {
        ae2utility$advancedMachineBranchTriggered = true;
    }

    @Inject(method = "onStackReturnedToNetwork", at = @At("HEAD"))
    private void ae2utility$prepareCraftReturnPulseAndLogTear(GenericStack genericStack, CallbackInfo ci) {
        boolean craftMode = ae2utility$shouldEmitFor(RedstoneSignalCardMode.CRAFT);
        boolean untilRecipeMode = ae2utility$isUntilRecipeMode();
        boolean waitingResultUnlock = unlockEvent == UnlockCraftingEvent.RESULT;
        boolean matchedLastPushOutputs = ae2utility$consumeReturnedOutput(genericStack);
        ae2utility$pendingCraftReturnRedstonePulse = genericStack != null && (craftMode || untilRecipeMode)
                && (waitingResultUnlock || matchedLastPushOutputs)
                && ae2utility$pendingOutputReturns.isEmpty();
        if (Ae2UtilityRedstoneSignalDebugLog.PULSE_TRACE) {
            Ae2UtilityRedstoneSignalDebugLog.pulse(
                    "adv_return_inventory_cb logic={} unlockEvent={} craftMode={} pendingPulse={} "
                            + "waitingResultUnlock={} matchedPushOutputs={} remainingOutputs={} stackBrief={}",
                    this.getClass().getName(),
                    unlockEvent != null ? unlockEvent.name() : "null",
                    craftMode || untilRecipeMode,
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
        if (ae2utility$isUntilRecipeMode()) {
            Ae2UtilityRedstoneSignalDebugLog.pulse(
                    "adv_until_recipe_lower_on_return logic={} stack={}",
                    this.getClass().getName(),
                    genericStack);
            ae2utility$disableContinuousSignalFromHost();
        } else {
            Ae2UtilityRedstoneSignalDebugLog.pulse(
                    "adv_craft_return_emit logic={} stack={}",
                    this.getClass().getName(),
                    genericStack);
            ae2utility$triggerSignalPulseFromHost();
        }
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
        ae2utility$pendingOutputReturns.clear();
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

    @Unique
    private void ae2utility$triggerSignalPulseFromHost() {
        try {
            java.lang.reflect.Field hostField = this.getClass().getDeclaredField("host");
            hostField.setAccessible(true);
            Object host = hostField.get(this);
            if (host == null) {
                Ae2UtilityRedstoneSignalDebugLog.pulse("adv_pulse_abort logic={} reason=reflection_host_null",
                        this.getClass().getName());
                return;
            }
            Object blockEntity = host.getClass().getMethod("getBlockEntity").invoke(host);
            if (!(blockEntity instanceof net.minecraft.world.level.block.entity.BlockEntity be)) {
                Ae2UtilityRedstoneSignalDebugLog.pulse(
                        "adv_pulse_abort logic={} reason=no_block_entity_from_host_getBlockEntity",
                        this.getClass().getName());
                return;
            }
            var level = be.getLevel();
            if (level == null || level.isClientSide) {
                Ae2UtilityRedstoneSignalDebugLog.pulse(
                        "adv_pulse_skip logic={} reason=level_null_or_client pos={}",
                        this.getClass().getName(),
                        be.getBlockPos());
                return;
            }
            boolean wasActive = ae2utility$hasSignalPulse(level.getGameTime());
            int durationTicks = ae2utility$resolveRedstoneOutputDurationTicks();
            ae2utility$triggerSignalPulse(level.getGameTime(), durationTicks);
            host.getClass().getMethod("saveChanges").invoke(host);
            Ae2UtilityRedstoneSignalDebugLog.pulse(
                    "adv_pulse_arm logic={} pos={} gameTime={} wasActiveBefore={} pulseUntilTick={} durationTicks={}",
                    this.getClass().getName(),
                    be.getBlockPos(),
                    level.getGameTime(),
                    wasActive,
                    ae2utility$getSignalPulseUntilTick(),
                    durationTicks);
            if (!wasActive) {
                level.updateNeighborsAt(be.getBlockPos(), be.getBlockState().getBlock());
                level.updateNeighbourForOutputSignal(be.getBlockPos(), be.getBlockState().getBlock());
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.scheduleTick(be.getBlockPos(), be.getBlockState().getBlock(), durationTicks);
                    Ae2UtilityRedstoneSignalDebugLog.pulse(
                            "adv_pulse_sched_tick logic={} pos={} delay={}",
                            this.getClass().getName(),
                            be.getBlockPos(),
                            durationTicks);
                }
            }
        } catch (ReflectiveOperationException ignored) {
            Ae2UtilityRedstoneSignalDebugLog.pulse(
                    "adv_pulse_skip logic={} reason=reflection_failed",
                    this.getClass().getName());
        }
    }

    @Unique
    private void ae2utility$enableContinuousSignalFromHost() {
        try {
            java.lang.reflect.Field hostField = this.getClass().getDeclaredField("host");
            hostField.setAccessible(true);
            Object host = hostField.get(this);
            if (host == null) {
                return;
            }
            Object blockEntity = host.getClass().getMethod("getBlockEntity").invoke(host);
            if (!(blockEntity instanceof net.minecraft.world.level.block.entity.BlockEntity be)) {
                return;
            }
            var level = be.getLevel();
            if (level == null || level.isClientSide || ae2utility$continuousSignalActive) {
                return;
            }
            ae2utility$continuousSignalActive = true;
            ae2utility$signalPulseUntilTick = 0;
            host.getClass().getMethod("saveChanges").invoke(host);
            level.updateNeighborsAt(be.getBlockPos(), be.getBlockState().getBlock());
            level.updateNeighbourForOutputSignal(be.getBlockPos(), be.getBlockState().getBlock());
        } catch (ReflectiveOperationException ignored) {
        }
    }

    @Unique
    private void ae2utility$disableContinuousSignalFromHost() {
        try {
            java.lang.reflect.Field hostField = this.getClass().getDeclaredField("host");
            hostField.setAccessible(true);
            Object host = hostField.get(this);
            if (host == null) {
                return;
            }
            Object blockEntity = host.getClass().getMethod("getBlockEntity").invoke(host);
            if (!(blockEntity instanceof net.minecraft.world.level.block.entity.BlockEntity be)) {
                return;
            }
            var level = be.getLevel();
            if (level == null || level.isClientSide || !ae2utility$continuousSignalActive) {
                return;
            }
            ae2utility$continuousSignalActive = false;
            ae2utility$signalPulseUntilTick = 0;
            host.getClass().getMethod("saveChanges").invoke(host);
            level.updateNeighborsAt(be.getBlockPos(), be.getBlockState().getBlock());
            level.updateNeighbourForOutputSignal(be.getBlockPos(), be.getBlockState().getBlock());
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
