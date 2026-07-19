package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.blockentity.crafting.PatternProviderBlockEntity;

import com.lhy.ae2utility.debug.Ae2UtilityRedstoneSignalDebugLog;
import com.lhy.ae2utility.integration.ae2.PatternProviderSignalAccess;

/**
 * 统一为 AE2 PatternProviderBlock 及其外部派生（ExPatternProvider / OverloadedPatternProvider）
 * 暴露红石信号输出能力。所有目标块的 BE 都是 {@link PatternProviderBlockEntity}。
 */
@Mixin(targets = {
        "appeng.block.crafting.PatternProviderBlock",
        "com.glodblock.github.extendedae.common.blocks.BlockExPatternProvider",
        "com.moakiee.ae2lt.block.OverloadedPatternProviderBlock"
}, remap = false)
public abstract class MixinAeStylePatternProviderBlock {

    public boolean isSignalSource(BlockState state) {
        return true;
    }

    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        BlockEntity raw = level.getBlockEntity(pos);
        int out = 0;
        if (raw instanceof PatternProviderBlockEntity be) {
            if (((PatternProviderSignalAccess) be.getLogic()).ae2utility$hasSignalPulse(be.getLevel().getGameTime())) {
                out = 15;
            }
        }
        Ae2UtilityRedstoneSignalDebugLog.wire(
                "ae_style_pp_block getSignal class={} pos={} side={} out={} be={}",
                this.getClass().getSimpleName(),
                pos, side, out,
                raw == null ? "null" : raw.getClass().getSimpleName());
        return out;
    }

    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getSignal(state, level, pos, side);
    }

    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Ae2UtilityRedstoneSignalDebugLog.wire("ae_style_pp_block scheduled_tick class={} pos={}",
                this.getClass().getSimpleName(), pos);
        level.updateNeighborsAt(pos, state.getBlock());
        level.updateNeighbourForOutputSignal(pos, state.getBlock());
    }
}
