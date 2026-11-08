package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

import com.lhy.ae2utility.integration.ae2.PatternProviderSignalAccess;

/**
 * ae2lt 过载样板供应器：用其每-tick 的 BE {@code serverTick} 作为红石双边沿状态机的稳定驱动，
 * 替代旧版 hook 它已被删/改的内部 push/return 方法（脆弱、需 require=0，曾导致 ae2lt 1.0.19 加载崩溃）。
 * <p>红石状态/接口实现继承自基类 {@link MixinPatternProviderLogic}（Overloaded extends AE2 PatternProviderLogic）。
 * ae2lt override 了 {@code getReturnInv()} 返回自己的 UnlimitedReturnInventory；无线下单的开始
 * 事件由 {@code MixinOverloadedPatternProviderLogic} 在自定义成功出口转发，本 Mixin 只负责
 * 用稳定 tick 观察返还库存和结束边沿。{@code allowCraftOnFall=true}：ae2lt 无逐键精确返还捕获，
 * CRAFT 走下降沿（buffer 排空）兜底。</p>
 */
@Mixin(targets = "com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity", remap = false)
public abstract class MixinOverloadedPatternProviderBlockEntity {

    @Inject(method = "serverTick", at = @At("RETURN"), require = 0)
    private static void ae2utility$driveRedstoneStateMachine(Level level, BlockPos pos, BlockState state,
            @Coerce Object blockEntity, CallbackInfo ci) {
        if (!(blockEntity instanceof PatternProviderLogicHost host)) {
            return;
        }
        PatternProviderLogic logic = host.getLogic();
        if (logic instanceof PatternProviderSignalAccess access) {
            boolean pendingOutput = !logic.getReturnInv().isEmpty()
                    || access.ae2utility$isPendingOutputTracking();
            access.ae2utility$tickRedstoneStateMachine(host, logic.isBusy(), pendingOutput, true);
        }
    }
}
