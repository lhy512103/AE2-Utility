package com.lhy.ae2utility.mixin;

import java.util.ArrayDeque;
import java.util.Deque;
import org.spongepowered.asm.mixin.Mixin;
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

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;

import com.lhy.ae2utility.card.NbtTearFilter;
import com.lhy.ae2utility.debug.NbtTearCardDebug;
import com.lhy.ae2utility.init.ModDataComponents;
import com.lhy.ae2utility.integration.ae2.NbtTearLogicAccess;
import com.lhy.ae2utility.integration.ae2.PatternProviderSignalAccess;
import com.lhy.ae2utility.item.NbtTearCardItem;

@Mixin(targets = "io.github.lounode.ae2cs.common.me.logic.ResonatingPatternProviderLogic", remap = false)
public abstract class MixinResonatingPatternProviderLogic implements NbtTearLogicAccess, PatternProviderSignalAccess {
    private static final String AE2UTILITY_TEAR_CARD = "ae2utility_nbt_tear_card";

    @Unique
    private final Deque<AEKey> ae2utility$markedSimulateKeys = new ArrayDeque<>();

    @Unique
    private final Deque<AEKey> ae2utility$markedModulateKeys = new ArrayDeque<>();

    @Unique
    private ItemStackHandler ae2utility$tearHandler;

    @Unique
    private int ae2utility$signalPulseUntilTick;

    @Unique
    private boolean ae2utility$continuousSignalActive;

    @Unique
    private boolean ae2utility$lastRedstoneActive;

    @Override
    public ItemStackHandler ae2utility$getTearHandler() {
        if (ae2utility$tearHandler == null) {
            var host = ((PatternProviderLogicInvoker) this).ae2utility$getHost();
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

    @Override
    public boolean ae2utility$getLastRedstoneActive() {
        return ae2utility$lastRedstoneActive;
    }

    @Override
    public void ae2utility$setLastRedstoneActive(boolean active) {
        ae2utility$lastRedstoneActive = active;
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void ae2utility$writeTearAndSignal(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
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
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void ae2utility$readTearAndSignal(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (tag.contains(AE2UTILITY_TEAR_CARD)) {
            ItemStack.parse(registries, tag.getCompound(AE2UTILITY_TEAR_CARD))
                    .ifPresent(s -> ae2utility$getTearHandler().setStackInSlot(0, s));
        } else {
            ae2utility$getTearHandler().setStackInSlot(0, ItemStack.EMPTY);
        }
        ae2utility$signalPulseUntilTick = tag.getInt("ae2utility_signal_pulse_until");
        ae2utility$continuousSignalActive = tag.getBoolean("ae2utility_continuous_signal_active");
    }

    @Inject(method = "addDrops", at = @At("TAIL"))
    private void ae2utility$dropDedicatedCard(java.util.List<ItemStack> drops, CallbackInfo ci) {
        ItemStack card = ae2utility$getTearHandler().getStackInSlot(0);
        if (!card.isEmpty()) {
            drops.add(card);
            ae2utility$getTearHandler().setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void ae2utility$clearDedicatedCardAndSignal(CallbackInfo ci) {
        ae2utility$getTearHandler().setStackInSlot(0, ItemStack.EMPTY);
        ae2utility$signalPulseUntilTick = 0;
        ae2utility$continuousSignalActive = false;
    }

    @Inject(method = "pushPattern", at = @At("HEAD"))
    private void ae2utility$initResolvedKeys(IPatternDetails patternDetails, KeyCounter[] inputHolder,
            CallbackInfoReturnable<Boolean> cir) {
        ae2utility$markedSimulateKeys.clear();
        ae2utility$markedModulateKeys.clear();
    }

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void ae2utility$clearResolvedKeys(CallbackInfoReturnable<Boolean> cir) {
        ae2utility$markedSimulateKeys.clear();
        ae2utility$markedModulateKeys.clear();
        if (!cir.getReturnValueZ()) {
            return;
        }
        var host = ((PatternProviderLogicInvoker) this).ae2utility$getHost();
        PatternProviderLogic logic = (PatternProviderLogic) (Object) this;
        // 上升沿采样；下降沿（UNTIL 拉低）靠继承基类的 sendStacksOut driver，CRAFT 靠基类 onStackReturnedToNetwork。
        ae2utility$tickRedstoneStateMachine(host, logic.isBusy(), !logic.getReturnInv().isEmpty(), false);
    }

    @Redirect(method = "pushPattern",
            at = @At(value = "INVOKE",
                    target = "Lio/github/lounode/ae2cs/common/me/logic/ResonatingPatternProviderLogic;removeFromRemaining([Lappeng/api/stacks/KeyCounter;Lappeng/api/stacks/AEKey;J)Z"))
    private boolean ae2utility$removeFromRemainingWithTear(KeyCounter[] remaining, AEKey expectedKey, long amount) {
        AEKey resolved = ae2utility$removeMatchingKey(remaining, expectedKey, amount);
        if (resolved == null) {
            NbtTearCardDebug.logFuzzyCraftSearch("resonating_remove", expectedKey, null, false, "no_matching_key");
            return false;
        }

        ae2utility$markedSimulateKeys.addLast(resolved);
        ae2utility$markedModulateKeys.addLast(resolved);
        NbtTearCardDebug.logFuzzyCraftSearch("resonating_remove", expectedKey, resolved, true, "resolved_key");
        return true;
    }

    @Redirect(method = "pushPattern",
            at = @At(value = "INVOKE",
                    target = "Lappeng/helpers/patternprovider/PatternProviderTarget;insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J"))
    private long ae2utility$insertResolvedMarkedKey(PatternProviderTarget target, AEKey key, long amount, Actionable mode) {
        if (mode == Actionable.SIMULATE && !ae2utility$markedSimulateKeys.isEmpty()) {
            AEKey resolved = ae2utility$markedSimulateKeys.removeFirst();
            NbtTearCardDebug.logFuzzyCraftSearch("resonating_insert_simulate", key, resolved, true, "resolved_key");
            return target.insert(resolved, amount, mode);
        }
        if (mode == Actionable.MODULATE && !ae2utility$markedModulateKeys.isEmpty()) {
            AEKey resolved = ae2utility$markedModulateKeys.removeFirst();
            NbtTearCardDebug.logFuzzyCraftSearch("resonating_insert_modulate", key, resolved, true, "resolved_key");
            return target.insert(resolved, amount, mode);
        }
        return target.insert(key, amount, mode);
    }

    @Unique
    private AEKey ae2utility$removeMatchingKey(KeyCounter[] remaining, AEKey expectedKey, long amount) {
        AEKey exact = ae2utility$removeExactKey(remaining, expectedKey, amount);
        if (exact != null) {
            return exact;
        }

        NbtTearFilter filter = ae2utility$getActiveTearFilter();
        if (filter == null) {
            return null;
        }

        for (KeyCounter counter : remaining) {
            for (var fuzzy : counter.findFuzzy(expectedKey, appeng.api.config.FuzzyMode.IGNORE_ALL)) {
                AEKey candidate = fuzzy.getKey();
                if (counter.get(candidate) < amount) {
                    continue;
                }
                if (!NbtTearFilter.matchesUnlockExpected(expectedKey, candidate, filter)) {
                    continue;
                }
                counter.remove(candidate, amount);
                return candidate;
            }
        }
        return null;
    }

    @Unique
    private static AEKey ae2utility$removeExactKey(KeyCounter[] remaining, AEKey expectedKey, long amount) {
        long toRemove = amount;
        for (KeyCounter counter : remaining) {
            long available = counter.get(expectedKey);
            if (available <= 0) {
                continue;
            }
            long take = Math.min(available, toRemove);
            counter.remove(expectedKey, take);
            toRemove -= take;
            if (toRemove <= 0) {
                return expectedKey;
            }
        }
        return null;
    }

    @Unique
    private NbtTearFilter ae2utility$getActiveTearFilter() {
        if (!((Object) this instanceof NbtTearLogicAccess access)) {
            return null;
        }
        ItemStack card = access.ae2utility$getEffectiveTearCardStack();
        if (card.isEmpty() || !(card.getItem() instanceof NbtTearCardItem)) {
            return null;
        }
        return card.getOrDefault(ModDataComponents.NBT_TEAR_FILTER, NbtTearFilter.DEFAULT);
    }
}
