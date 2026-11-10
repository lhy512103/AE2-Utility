package com.lhy.ae2utility.mixin;

import java.util.List;
import java.util.Optional;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.lounode.ae2cs.common.me.crafting.EncodedResonatingPattern.Target;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;

import com.lhy.ae2utility.card.NbtTearFilter;
import com.lhy.ae2utility.integration.ae2cs.Ae2CsRoutingAdapter;
import com.lhy.ae2utility.integration.ae2cs.RoutingAttempt;
import com.lhy.ae2utility.integration.ae2.NbtTearLogicAccess;
import com.lhy.ae2utility.integration.ae2.PatternProviderFeatureCardCache;
import com.lhy.ae2utility.integration.ae2.PatternProviderSignalAccess;
import com.lhy.ae2utility.item.NbtTearCardItem;

@Mixin(targets = "io.github.lounode.ae2cs.common.me.logic.ResonatingPatternProviderLogic", remap = false)
public abstract class MixinResonatingPatternProviderLogic implements NbtTearLogicAccess, PatternProviderSignalAccess {
    private static final String AE2UTILITY_TEAR_CARD = "ae2utility_nbt_tear_card";

    @Unique
    private RoutingAttempt<Target, PatternProviderTarget> ae2utility$routingAttempt;

    @Unique
    private ItemStackHandler ae2utility$tearHandler;

    @Unique
    private PatternProviderFeatureCardCache ae2utility$featureCardCache;

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
                    if (ae2utility$featureCardCache != null) {
                        ae2utility$featureCardCache.invalidate();
                    }
                    host.saveChanges();
                }
            };
        }
        return ae2utility$tearHandler;
    }

    @Override
    public PatternProviderFeatureCardCache ae2utility$getFeatureCardCache() {
        if (ae2utility$featureCardCache == null) {
            ae2utility$featureCardCache = new PatternProviderFeatureCardCache(this,
                    () -> ae2utility$getTearHandler().getStackInSlot(0));
        }
        return ae2utility$featureCardCache;
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
    private void ae2utility$writeTearAndSignal(CompoundTag tag, CallbackInfo ci) {
        ItemStack card = ae2utility$getTearHandler().getStackInSlot(0);
        if (!card.isEmpty()) {
            tag.put(AE2UTILITY_TEAR_CARD, card.save(new CompoundTag()));
        }
        if (ae2utility$signalPulseUntilTick > 0) {
            tag.putInt("ae2utility_signal_pulse_until", ae2utility$signalPulseUntilTick);
        }
        if (ae2utility$continuousSignalActive) {
            tag.putBoolean("ae2utility_continuous_signal_active", true);
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void ae2utility$readTearAndSignal(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(AE2UTILITY_TEAR_CARD)) {
            java.util.Optional.of(ItemStack.of(tag.getCompound(AE2UTILITY_TEAR_CARD)))
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

    @WrapMethod(method = "pushPattern")
    private boolean ae2utility$withRoutingAttempt(IPatternDetails patternDetails, KeyCounter[] inputHolder,
            Operation<Boolean> original) {
        var previous = ae2utility$routingAttempt;
        var attempt = new RoutingAttempt<Target, PatternProviderTarget>();
        ae2utility$routingAttempt = attempt;
        try {
            boolean accepted = original.call(patternDetails, inputHolder);
            if (accepted && attempt.isRouted()) {
                // Includes ordinary processing patterns routed through the new provider defaults.
                // The superclass fallback already sends its own successful-push notification.
                var host = ((PatternProviderLogicInvoker) this).ae2utility$getHost();
                PatternProviderLogic logic = (PatternProviderLogic) (Object) this;
                ae2utility$onSuccessfulPatternPush(host, logic.isBusy(), !logic.getReturnInv().isEmpty());
            }
            return accepted;
        } catch (Ae2CsRoutingAdapter.UnresolvedInputs rejectedBeforeSending) {
            // Resolution occurs before native target simulation/insertion. The CPU still owns every input.
            return false;
        } finally {
            ae2utility$routingAttempt = previous;
        }
    }

    @WrapOperation(method = "pushPattern", require = 1,
            at = @At(value = "INVOKE",
                    target = "Lio/github/lounode/ae2cs/common/me/logic/ResonatingPatternProviderLogic$RoutedPatternData;sparseInputs()Ljava/util/List;"))
    private List<GenericStack> ae2utility$resolveSparseInputs(@Coerce Object routed,
            Operation<List<GenericStack>> original, IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        // Semantic typed producer, not a compiler-assigned local slot. Native Marked and local lists,
        // simulation, modulation and persisted overflow all receive these SAME actual AEKeys.
        return Ae2CsRoutingAdapter.resolve(original.call(routed), inputHolder,
                ae2utility$getActiveTearFilter(), ae2utility$routingAttempt);
    }

    @WrapOperation(method = "pushPattern", require = 1,
            at = @At(value = "INVOKE",
                    target = "Lio/github/lounode/ae2cs/common/me/logic/ResonatingPatternProviderLogic$RoutedPatternData;getTargetForSparseInputIndex(I)Ljava/util/Optional;"))
    private Optional<Target> ae2utility$preserveSparseRouting(@Coerce Object routed, int resolvedIndex,
            Operation<Optional<Target>> original) {
        return original.call(routed, ae2utility$routingAttempt.sourceIndex(resolvedIndex));
    }

    @WrapOperation(method = "pushPattern", require = 1,
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lio/github/lounode/ae2cs/common/me/logic/ResonatingPatternProviderLogic;findTarget(Lio/github/lounode/ae2cs/common/me/crafting/EncodedResonatingPattern$Target;)Lappeng/helpers/patternprovider/PatternProviderTarget;"))
    private PatternProviderTarget ae2utility$rememberSimulatedTarget(@Coerce Object logic, Target target,
            Operation<PatternProviderTarget> original) {
        return ae2utility$routingAttempt.target(target, () -> original.call(logic, target));
    }

    @WrapOperation(method = "pushPattern", require = 1,
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Lio/github/lounode/ae2cs/common/me/logic/ResonatingPatternProviderLogic;findTarget(Lio/github/lounode/ae2cs/common/me/crafting/EncodedResonatingPattern$Target;)Lappeng/helpers/patternprovider/PatternProviderTarget;"))
    private PatternProviderTarget ae2utility$bufferUnavailableCommitTarget(@Coerce Object logic, Target target,
            Operation<PatternProviderTarget> original) {
        // Native otherwise returns false here AFTER earlier remote destinations may have accepted items.
        // Recheck availability (never insert into a detached cached capability), but on late absence
        // accept ownership and let native PendingSend preserve the complete actual-key remainder.
        return ae2utility$routingAttempt.commitTarget(target, () -> original.call(logic, target),
                Ae2CsRoutingAdapter.BUFFER_ONLY_TARGET);
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
        return com.lhy.ae2utility.item.NbtTearCardItem.getFilter(card);
    }
}
