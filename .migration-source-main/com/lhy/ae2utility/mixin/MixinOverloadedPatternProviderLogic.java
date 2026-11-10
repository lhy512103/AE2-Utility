package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

import com.lhy.ae2utility.integration.ae2.PatternProviderSignalAccess;

/**
 * 闪电科技过载样板供应器的自定义派发兼容。
 *
 * <p>普通非定向样板会进入 AE2 的基类 pushPattern，由 MixinPatternProviderLogic 处理；
 * 这里钩住 ae2lt 自己的定向、无线和批量派发成功出口，避免重复发出 ORDER 信号。
 * 无线模式的 isBusy() 固定为 false，不能只依赖基类状态机的 tick 采样。</p>
 *
 * <p>2.1.3 将逻辑移动至 logic.provider 包，但仍继承 AE2 PatternProviderLogic。
 * 两个目标互斥存在，Pseudo 仅允许缺席的版本目标跳过，实际成功事件仍完整注入。</p>
 */
@Pseudo
@Mixin(targets = {
        "com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic",
        "com.moakiee.ae2lt.logic.provider.OverloadedPatternProviderLogic"
}, remap = false)
public abstract class MixinOverloadedPatternProviderLogic {

    @Inject(method = "wirelessPushPattern", at = @At("RETURN"), require = 0)
    private void ae2utility$wirelessPushReturn(IPatternDetails pattern, KeyCounter[] inputs,
            CallbackInfoReturnable<Boolean> cir) {
        ae2utility$notifySuccessfulCustomPush(pattern, cir.getReturnValueZ());
    }

    @Inject(method = "pushPatternDirectionally", at = @At("RETURN"), require = 0)
    private void ae2utility$directionalPushReturn(IPatternDetails pattern, KeyCounter[] inputs,
            CallbackInfoReturnable<Boolean> cir) {
        ae2utility$notifySuccessfulCustomPush(pattern, cir.getReturnValueZ());
    }

    /**
     * Thunderbolt returns the number of copies LEFT, not the accepted count.
     * Hook the two adaptive dispatch implementations rather than pushBatch itself:
     * its non-adaptive fallback calls pushPattern and is already reported there.
     * pushNormalBatch is also used by SAME_PATTERN single-copy dispatch.
     * PatternInputAcceptance moved packages with the logic, so coerce only that enum.
     */
    @Inject(method = "pushNormalBatch", at = @At("RETURN"), require = 0)
    private void ae2utility$normalBatchPushReturn(IPatternDetails pattern, KeyCounter[] inputs,
            long requested, @Coerce Object acceptance, CallbackInfoReturnable<Long> cir) {
        ae2utility$notifySuccessfulCustomPush(pattern, requested > 0 && cir.getReturnValueJ() < requested);
    }

    @Inject(method = "pushWirelessBatch", at = @At("RETURN"), require = 0)
    private void ae2utility$wirelessBatchPushReturn(IPatternDetails pattern, KeyCounter[] inputs,
            long requested, CallbackInfoReturnable<Long> cir) {
        ae2utility$notifySuccessfulCustomPush(pattern, requested > 0 && cir.getReturnValueJ() < requested);
    }

    @Unique
    private void ae2utility$notifySuccessfulCustomPush(IPatternDetails pattern, boolean accepted) {
        if (!accepted || !((Object) this instanceof PatternProviderSignalAccess access)) {
            return;
        }

        PatternProviderLogic logic = (PatternProviderLogic) (Object) this;
        PatternProviderLogicHost host = ((PatternProviderLogicInvoker) this).ae2utility$getHost();
        var returnInventory = (Object) this instanceof OverloadedPatternProviderLogicAccessor fullInventory
                ? fullInventory.ae2utility$getInternalReturnInv()
                : logic.getReturnInv();
        access.ae2utility$onSuccessfulPatternPush(host, logic.isBusy(), !returnInventory.isEmpty());
    }
}
