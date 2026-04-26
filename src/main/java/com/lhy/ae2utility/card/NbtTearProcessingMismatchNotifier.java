package com.lhy.ae2utility.card;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.lhy.ae2utility.Ae2UtilityMod;

/**
 * 处理配方在「网络中有 NBT 差异可匹配」但「提供该配方的供应器均未装撕裂卡」时提示玩家。
 */
public final class NbtTearProcessingMismatchNotifier {
    private NbtTearProcessingMismatchNotifier() {
    }

    public static void tryNotify(IPatternDetails pattern, GenericStack templateStack, AEKey actualInputKey) {
        if (pattern == null || templateStack == null || actualInputKey == null) {
            return;
        }
        AEKey templateKey = templateStack.what();
        String dedupe = pattern.getDefinition()
                + "|"
                + templateKey.toString()
                + "|"
                + actualInputKey.toString();
        if (!NbtTearSimulationEnv.shouldEmitProcessingTearHint(dedupe)) {
            return;
        }
        var requester = NbtTearSimulationEnv.getRequester();
        if (requester == null) {
            return;
        }
        IActionSource src = requester.getActionSource();
        if (src == null) {
            return;
        }
        var player = src.player().orElse(null);
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        sp.sendSystemMessage(
                Component.translatable(
                        "ae2utility.nbt_tear.processing_provider_missing_card",
                        templateKey.getDisplayName(),
                        actualInputKey.getDisplayName(),
                        pattern.getPrimaryOutput().what().getDisplayName()));
        Ae2UtilityMod.LOGGER.debug(
                "[ae2utility] processing tear hint: pattern={} template={} actual={}",
                pattern.getDefinition(),
                templateKey,
                actualInputKey);
    }
}
