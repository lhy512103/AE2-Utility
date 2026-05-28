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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

import appeng.helpers.patternprovider.UnlockCraftingEvent;

import com.lhy.ae2utility.card.NbtTearCardThreadLocal;
import com.lhy.ae2utility.debug.Ae2UtilityRedstoneSignalDebugLog;
import com.lhy.ae2utility.card.NbtTearFilter;
import com.lhy.ae2utility.card.RedstoneSignalCardMode;
import com.lhy.ae2utility.debug.NbtTearCardDebug;
import com.lhy.ae2utility.init.ModDataComponents;
import com.lhy.ae2utility.integration.ae2.NbtTearLogicAccess;
import com.lhy.ae2utility.integration.ae2.PatternProviderSignalAccess;
import com.lhy.ae2utility.item.NbtTearCardItem;

@Mixin(PatternProviderLogic.class)
public abstract class MixinPatternProviderLogic implements NbtTearLogicAccess, PatternProviderSignalAccess {

    private static final String AE2UTILITY_TEAR_CARD = "ae2utility_nbt_tear_card";
    private static final String AE2UTILITY_PENDING_OUTPUTS = "ae2utility_pending_outputs";

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
     * 推样板成功后收集的产物键：用于 CRAFT 回传脉冲匹配；在 {@code LOCK_UNTIL_RESULT} 时亦供撕裂卡 {@code equals} 重定向。
     */
    @Unique
    private final List<AEKey> ae2utility$unlockOutputWhats = new ArrayList<>();

    @Unique
    private final List<GenericStack> ae2utility$pendingOutputReturns = new ArrayList<>();

    @Unique
    private int ae2utility$signalPulseUntilTick;

    @Unique
    private boolean ae2utility$continuousSignalActive;

    /** 产物回传：仅当本次样板的待回网产物全部扣减完毕后才发射/结束红石信号。 */
    @Unique
    private boolean ae2utility$pendingCraftReturnRedstonePulse;

    /** 待发 {@code sendList} 清空后再发下单脉冲（见 {@link #ae2utility$emitOrderPulseWhenDeferredSendListDrained}）。 */
    @Unique
    private boolean ae2utility$orderPulseDeferredUntilSendClears;

    @Shadow
    public abstract boolean isBusy();

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
            Ae2UtilityRedstoneSignalDebugLog.pulse("until_recipe_raise_after_sendlist_drained host={}",
                    host.getClass().getName());
            ae2utility$enableContinuousSignalFromHost();
        } else {
            Ae2UtilityRedstoneSignalDebugLog.pulse("order_emit_after_sendlist_drained host={}", host.getClass().getName());
            ae2utility$triggerSignalPulseFromHost();
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
        if (!cir.getReturnValueZ()) {
            return;
        }
        boolean orderMode = ae2utility$shouldEmitFor(RedstoneSignalCardMode.ORDER);
        boolean untilRecipeMode = ae2utility$isUntilRecipeMode();
        if (!orderMode && !untilRecipeMode) {
            Ae2UtilityRedstoneSignalDebugLog.pulse("order_gate_skip host={} no_order_or_until_mode_or_card",
                    host.getClass().getName());
            return;
        }
        if (isBusy()) {
            ae2utility$orderPulseDeferredUntilSendClears = true;
            Ae2UtilityRedstoneSignalDebugLog.pulse("order_or_until_emit_deferred_until_sendlist host={}",
                    host.getClass().getName());
            return;
        }
        ae2utility$orderPulseDeferredUntilSendClears = false;
        if (untilRecipeMode) {
            Ae2UtilityRedstoneSignalDebugLog.pulse("until_recipe_raise_after_dispatch host={}",
                    host.getClass().getName());
            ae2utility$enableContinuousSignalFromHost();
        } else {
            Ae2UtilityRedstoneSignalDebugLog.pulse("order_emit_after_dispatch host={}", host.getClass().getName());
            ae2utility$triggerSignalPulseFromHost();
        }
    }

    @Inject(method = "onStackReturnedToNetwork", at = @At("HEAD"))
    private void ae2utility$prepareCraftReturnPulseAndLogTear(GenericStack genericStack, CallbackInfo ci) {
        boolean craftMode = ae2utility$shouldEmitFor(RedstoneSignalCardMode.CRAFT);
        boolean untilRecipeMode = ae2utility$isUntilRecipeMode();
        boolean waitingResultUnlock = unlockEvent == UnlockCraftingEvent.RESULT;
        boolean matchedPendingOutputs = ae2utility$consumeReturnedOutput(genericStack);
        ae2utility$pendingCraftReturnRedstonePulse = genericStack != null && (craftMode || untilRecipeMode)
                && (waitingResultUnlock || matchedPendingOutputs)
                && ae2utility$pendingOutputReturns.isEmpty();
        if (Ae2UtilityRedstoneSignalDebugLog.PULSE_TRACE) {
            Ae2UtilityRedstoneSignalDebugLog.pulse(
                    "return_inventory_cb host={} unlockEvent={} craftMode={} pendingPulse={} "
                            + "waitingResultUnlock={} matchedPendingOutputs={} remainingOutputs={} stackBrief={}",
                    host.getClass().getName(),
                    unlockEvent != null ? unlockEvent.name() : "null",
                    craftMode || untilRecipeMode,
                    ae2utility$pendingCraftReturnRedstonePulse,
                    waitingResultUnlock,
                    matchedPendingOutputs,
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
            Ae2UtilityRedstoneSignalDebugLog.pulse("until_recipe_lower_on_return host={} stack={}",
                    host.getClass().getName(),
                    genericStack);
            ae2utility$disableContinuousSignalFromHost();
        } else {
            Ae2UtilityRedstoneSignalDebugLog.pulse("craft_return_emit host={} stack={}",
                    host.getClass().getName(),
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
                        "pending_output_consume host={} matched={} returnedAmount={} remainingAmount={} remainingEntries={}",
                        host.getClass().getName(),
                        expected.what(),
                        genericStack.amount(),
                        Math.max(remaining, 0),
                        ae2utility$pendingOutputReturns.size());
            }
            return true;
        }
        return false;
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
                    "pending_output_rebuild host={} unlockEvent={} outputs={}",
                    host.getClass().getName(),
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

    @Unique
    private void ae2utility$triggerSignalPulseFromHost() {
        var blockEntity = host.getBlockEntity();
        if (blockEntity == null) {
            Ae2UtilityRedstoneSignalDebugLog.pulse("pulse_abort host_class={} reason=no_block_entity",
                    host.getClass().getName());
            return;
        }
        var level = blockEntity.getLevel();
        if (level == null || level.isClientSide) {
            Ae2UtilityRedstoneSignalDebugLog.pulse(
                    "pulse_skip host_class={} reason=level_null_or_client pos={}",
                    host.getClass().getName(),
                    blockEntity.getBlockPos());
            return;
        }
        boolean wasActive = ae2utility$hasSignalPulse(level.getGameTime());
        int durationTicks = ae2utility$resolveRedstoneOutputDurationTicks();
        ae2utility$triggerSignalPulse(level.getGameTime(), durationTicks);
        host.saveChanges();
        Ae2UtilityRedstoneSignalDebugLog.pulse(
                "pulse_arm host_class={} pos={} gameTime={} wasActiveBefore={} pulseUntilTick={} durationTicks={}",
                host.getClass().getName(),
                blockEntity.getBlockPos(),
                level.getGameTime(),
                wasActive,
                ae2utility$getSignalPulseUntilTick(),
                durationTicks);
        if (!wasActive) {
            level.updateNeighborsAt(blockEntity.getBlockPos(), blockEntity.getBlockState().getBlock());
            level.updateNeighbourForOutputSignal(blockEntity.getBlockPos(), blockEntity.getBlockState().getBlock());
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.scheduleTick(blockEntity.getBlockPos(), blockEntity.getBlockState().getBlock(), durationTicks);
                Ae2UtilityRedstoneSignalDebugLog.pulse("pulse_sched_tick host_class={} pos={} delay={}",
                        host.getClass().getName(),
                        blockEntity.getBlockPos(),
                        durationTicks);
            }
        }
    }

    @Unique
    private void ae2utility$enableContinuousSignalFromHost() {
        var blockEntity = host.getBlockEntity();
        if (blockEntity == null) {
            return;
        }
        var level = blockEntity.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        if (ae2utility$continuousSignalActive) {
            return;
        }
        ae2utility$continuousSignalActive = true;
        ae2utility$signalPulseUntilTick = 0;
        host.saveChanges();
        level.updateNeighborsAt(blockEntity.getBlockPos(), blockEntity.getBlockState().getBlock());
        level.updateNeighbourForOutputSignal(blockEntity.getBlockPos(), blockEntity.getBlockState().getBlock());
    }

    @Unique
    private void ae2utility$disableContinuousSignalFromHost() {
        var blockEntity = host.getBlockEntity();
        if (blockEntity == null) {
            return;
        }
        var level = blockEntity.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        if (!ae2utility$continuousSignalActive) {
            return;
        }
        ae2utility$continuousSignalActive = false;
        ae2utility$signalPulseUntilTick = 0;
        host.saveChanges();
        level.updateNeighborsAt(blockEntity.getBlockPos(), blockEntity.getBlockState().getBlock());
        level.updateNeighbourForOutputSignal(blockEntity.getBlockPos(), blockEntity.getBlockState().getBlock());
    }
}
