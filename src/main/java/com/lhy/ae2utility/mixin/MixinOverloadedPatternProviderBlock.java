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

@Mixin(targets = "com.moakiee.ae2lt.block.OverloadedPatternProviderBlock", remap = false)
public abstract class MixinOverloadedPatternProviderBlock {

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
                "ae2lt.OverloadedPatternProviderBlock getSignal pos={} side={} out={} be={}",
                pos,
                side,
                out,
                raw == null ? "null" : raw.getClass().getSimpleName());
        return out;
    }

    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getSignal(state, level, pos, side);
    }

    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Ae2UtilityRedstoneSignalDebugLog.wire("ae2lt.OverloadedPatternProviderBlock scheduled_tick pos={}", pos);
        level.updateNeighborsAt(pos, state.getBlock());
        level.updateNeighbourForOutputSignal(pos, state.getBlock());
    }
}
