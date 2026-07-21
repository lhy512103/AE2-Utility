package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
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
 * 这里仅钩住 ae2lt 自己的定向和无线成功出口，避免重复发出 ORDER 信号。无线模式的
 * isBusy() 固定为 false，因此不能只依赖基类状态机的 tick 采样。</p>
 */
@Mixin(targets = "com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic", remap = false)
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

    @Unique
    private void ae2utility$notifySuccessfulCustomPush(IPatternDetails pattern, boolean accepted) {
        if (!accepted || !((Object) this instanceof PatternProviderSignalAccess access)) {
            return;
        }

        PatternProviderLogic logic = (PatternProviderLogic) (Object) this;
        PatternProviderLogicHost host = ((PatternProviderLogicInvoker) this).ae2utility$getHost();
        access.ae2utility$onSuccessfulPatternPush(host, logic.isBusy(), !logic.getReturnInv().isEmpty());
    }
}
