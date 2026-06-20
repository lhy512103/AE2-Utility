package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.helpers.patternprovider.PatternProviderLogic;

import com.lhy.ae2utility.card.RedstoneSignalCardMode;
import com.lhy.ae2utility.debug.Ae2UtilityRedstoneSignalDebugLog;
import com.lhy.ae2utility.integration.ae2.PatternProviderSignalAccess;

@Mixin(targets = "com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic", remap = false)
public abstract class MixinOverloadedPatternProviderLogic {

    @Unique
    private boolean ae2utility$lastPushWasWireless;

    @Inject(method = "pushPattern", at = @At("HEAD"))
    private void ae2utility$resetPushMode(IPatternDetails patternDetails, KeyCounter[] inputHolder,
            CallbackInfoReturnable<Boolean> cir) {
        ae2utility$lastPushWasWireless = false;
    }

    @Inject(method = "wirelessPushPattern", at = @At("HEAD"))
    private void ae2utility$markWirelessPush(IPatternDetails pattern, KeyCounter[] inputs,
            CallbackInfoReturnable<Boolean> cir) {
        ae2utility$lastPushWasWireless = true;
    }

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void ae2utility$emitNormalOrFallbackOrderSignal(IPatternDetails patternDetails, KeyCounter[] inputHolder,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || ae2utility$lastPushWasWireless) {
            return;
        }
        ae2utility$emitOrderOrUntilSignal("overloaded_normal_dispatch");
    }

    @Inject(method = "wirelessPushPattern", at = @At("RETURN"))
    private void ae2utility$emitWirelessOrderSignal(IPatternDetails pattern, KeyCounter[] inputs,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        ae2utility$emitOrderOrUntilSignal("overloaded_wireless_dispatch");
    }

    // ae2lt 1.0.19 重构后删除了 insertOutputsToReturnInv / returnToNetwork；require=0 让目标缺失时优雅降级，
    // 不再因找不到注入目标而炸全局（其余 insertOutputsToReturnInv / returnToNetwork 注入早已 require=0）。
    @Inject(method = "insertOutputsToReturnInv", at = @At("HEAD"), require = 0)
    private void ae2utility$prepareBufferedReturnSignal(java.util.List<GenericStack> outputs,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        Ae2UtilityRedstoneSignalDebugLog.pulse(
                "overloaded_buffer_return_attempt logic={} outputs={}",
                this.getClass().getName(),
                outputs == null ? "null" : outputs.size());
    }

    @Unique
    private void ae2utility$emitOrderOrUntilSignal(String source) {
        if (!((Object) this instanceof PatternProviderSignalAccess access)) {
            return;
        }
        if ((Object) this instanceof PatternProviderLogic logic && logic.isBusy()) {
            Ae2UtilityRedstoneSignalDebugLog.pulse("{} skip_busy logic={}", source, this.getClass().getName());
            return;
        }
        if (access.ae2utility$isUntilRecipeMode()) {
            Ae2UtilityRedstoneSignalDebugLog.pulse("{} until_raise logic={}", source, this.getClass().getName());
            access.ae2utility$enableContinuousSignalFromHost(this);
        } else if (access.ae2utility$shouldEmitFor(RedstoneSignalCardMode.ORDER)) {
            Ae2UtilityRedstoneSignalDebugLog.pulse("{} order_emit logic={}", source, this.getClass().getName());
            access.ae2utility$triggerSignalPulseFromHost(this);
        }
    }

    @Redirect(
            method = "returnToNetwork",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/storage/MEStorage;insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J"),
            require = 0)
    private long ae2utility$notifyDirectReturnInsertedLegacy(MEStorage storage, AEKey key, long amount, Actionable mode,
            IActionSource source) {
        return ae2utility$handleDirectReturnInsert(storage, key, amount, mode, source);
    }

    @Redirect(
            method = "lambda$returnToNetwork$9",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/api/storage/MEStorage;insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)J"),
            require = 0)
    private long ae2utility$notifyDirectReturnInsertedLambda(MEStorage storage, AEKey key, long amount, Actionable mode,
            IActionSource source) {
        return ae2utility$handleDirectReturnInsert(storage, key, amount, mode, source);
    }

    @Unique
    private long ae2utility$handleDirectReturnInsert(MEStorage storage, AEKey key, long amount, Actionable mode,
            IActionSource source) {
        long inserted = storage.insert(key, amount, mode, source);
        Ae2UtilityRedstoneSignalDebugLog.pulse(
                "overloaded_direct_return_insert logic={} key={} requested={} inserted={} mode={}",
                this.getClass().getName(),
                key,
                amount,
                inserted,
                mode);
        if (mode == Actionable.MODULATE && inserted > 0) {
            ((PatternProviderLogicInvoker) this).ae2utility$onStackReturnedToNetwork(new GenericStack(key, inserted));
        }
        return inserted;
    }

    @Redirect(
            method = "insertOutputsToReturnInv",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/helpers/patternprovider/PatternProviderReturnInventory;insert(ILappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J"),
            require = 0)
    private long ae2utility$logBufferedReturnInsertedLegacy(appeng.helpers.patternprovider.PatternProviderReturnInventory inventory,
            int slot, AEKey key, long amount, Actionable mode) {
        long inserted = inventory.insert(slot, key, amount, mode);
        Ae2UtilityRedstoneSignalDebugLog.pulse(
                "overloaded_buffer_return_insert_legacy logic={} key={} requested={} inserted={} mode={}",
                this.getClass().getName(),
                key,
                amount,
                inserted,
                mode);
        return inserted;
    }

    @Redirect(
            method = "insertOutputsToReturnInv",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/moakiee/ae2lt/logic/UnlimitedReturnInventory;insert(ILappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J"),
            require = 0)
    private long ae2utility$logBufferedReturnInsertedAe2lt107(@Coerce Object inventory, int slot, AEKey key,
            long amount, Actionable mode) {
        long inserted = ae2utility$invokeReturnInventoryInsert(inventory, slot, key, amount, mode);
        Ae2UtilityRedstoneSignalDebugLog.pulse(
                "overloaded_buffer_return_insert logic={} key={} requested={} inserted={} mode={}",
                this.getClass().getName(),
                key,
                amount,
                inserted,
                mode);
        return inserted;
    }

    @Unique
    private long ae2utility$invokeReturnInventoryInsert(Object inventory, int slot, AEKey key, long amount,
            Actionable mode) {
        if (inventory == null) {
            return 0;
        }
        try {
            Object result = inventory.getClass()
                    .getMethod("insert", int.class, AEKey.class, long.class, Actionable.class)
                    .invoke(inventory, slot, key, amount, mode);
            return result instanceof Long inserted ? inserted : 0;
        } catch (ReflectiveOperationException e) {
            Ae2UtilityRedstoneSignalDebugLog.pulse(
                    "overloaded_buffer_return_insert_reflection_failed logic={} inventory={} key={} requested={}",
                    this.getClass().getName(),
                    inventory.getClass().getName(),
                    key,
                    amount);
            return 0;
        }
    }
}
