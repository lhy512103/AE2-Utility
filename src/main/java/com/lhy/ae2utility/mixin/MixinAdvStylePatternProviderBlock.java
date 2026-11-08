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

@Mixin(value = {
        net.pedroksl.advanced_ae.common.blocks.AdvPatternProviderBlock.class,
        net.pedroksl.advanced_ae.common.blocks.SmallAdvPatternProviderBlock.class
})
public abstract class MixinAdvStylePatternProviderBlock {
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        int out = 0;
        if (blockEntity != null && ae2utility$hasSignalPulse(blockEntity)) {
            out = 15;
        }
        Ae2UtilityRedstoneSignalDebugLog.wire(
                "adv_style_pp_block getSignal class={} pos={} side={} out={} be={}",
                this.getClass().getSimpleName(), pos, side, out,
                blockEntity == null ? "null" : blockEntity.getClass().getSimpleName());
        return out;
    }

    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getSignal(state, level, pos, side);
    }

    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Ae2UtilityRedstoneSignalDebugLog.wire("adv_style_pp_block scheduled_tick class={} pos={}",
                this.getClass().getSimpleName(), pos);
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
