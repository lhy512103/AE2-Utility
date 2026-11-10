package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
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
 * <p>已核验 MC 26.1.2 的 1.0.1alpha 字节码：NORMAL 分支调用 AE2 的基类
 * pushPattern，WIRELESS 分支调用 wirelessPushPattern。仅在后者成功时转发信号，
 * 避免普通模式重复发出 ORDER 信号。此发行版没有其他 MC 版本的定向或批量派发方法。</p>
 * <p>Pseudo 只隔离未安装的可选模组；已安装时注入必须成功。</p>
 */
@Pseudo
@Mixin(targets = {
        "com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic"
}, remap = false)
public abstract class MixinOverloadedPatternProviderLogic {

    @Inject(method = "wirelessPushPattern", at = @At("RETURN"))
    private void ae2utility$wirelessPushReturn(IPatternDetails pattern, KeyCounter[] inputs,
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
        var returnInventory = (Object) this instanceof OverloadedPatternProviderLogicAccessor fullInventory
                ? fullInventory.ae2utility$getInternalReturnInv()
                : logic.getReturnInv();
        access.ae2utility$onSuccessfulPatternPush(host, logic.isBusy(), !returnInventory.isEmpty());
    }
}
