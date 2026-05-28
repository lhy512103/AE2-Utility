package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.lhy.ae2utility.debug.Ae2UtilityRedstoneSignalDebugLog;
import com.lhy.ae2utility.integration.ae2.PatternProviderSignalAccess;

@Mixin(targets = "net.pedroksl.advanced_ae.common.blocks.AdvPatternProviderBlock", remap = false)
public abstract class MixinAdvPatternProviderBlock {

    public boolean isSignalSource(BlockState state) {
        return true;
    }

    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        int out = 0;
        if (blockEntity != null
                && "net.pedroksl.advanced_ae.common.entities.AdvPatternProviderEntity".equals(blockEntity.getClass().getName())
                && ae2utility$hasSignalPulse(blockEntity)) {
            out = 15;
        }
        Ae2UtilityRedstoneSignalDebugLog.wire(
                "adv_pp_block getSignal pos={} side={} out={} be={}",
                pos,
                side,
                out,
                blockEntity == null ? "null" : blockEntity.getClass().getSimpleName());
        return out;
    }

    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getSignal(state, level, pos, side);
    }

    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Ae2UtilityRedstoneSignalDebugLog.wire("adv_pp_block scheduled_tick pos={}", pos);
        level.updateNeighborsAt(pos, state.getBlock());
        level.updateNeighbourForOutputSignal(pos, state.getBlock());
    }

    private static boolean ae2utility$hasSignalPulse(BlockEntity blockEntity) {
        try {
            Object logic = blockEntity.getClass().getMethod("getLogic").invoke(blockEntity);
            if (logic instanceof PatternProviderSignalAccess access && blockEntity.getLevel() != null) {
                return access.ae2utility$hasSignalPulse(blockEntity.getLevel().getGameTime());
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return false;
    }
}
