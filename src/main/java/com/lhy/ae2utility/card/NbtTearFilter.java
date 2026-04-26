package com.lhy.ae2utility.card;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/**
 * Stored on an NBT tear card. Empty {@link #itemIds()} = treat all item outputs as item-id-only when matching.
 * Non-empty = only those items use item-id-only matching; others stay strict.
 */
public record NbtTearFilter(List<ResourceLocation> itemIds) {
    public static final NbtTearFilter DEFAULT = new NbtTearFilter(List.of());

    public static final Codec<NbtTearFilter> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    ResourceLocation.CODEC.listOf().fieldOf("item_ids").forGetter(NbtTearFilter::itemIds))
                    .apply(instance, NbtTearFilter::new));

    public static boolean matchesUnlockExpected(GenericStack expected, GenericStack returned, NbtTearFilter filter) {
        if (expected == null || returned == null) {
            return false;
        }
        return matchesUnlockExpected(expected.what(), returned.what(), filter);
    }

    /**
     * 与样板供应器「合成完毕」解锁比较：先严格 {@link AEKey#equals}；物品键则按 {@link Item} 身份比较（忽略 DataComponents/NBT），
     * 白名单非空时仅对白名单内物品放宽；其它键类型再尝试 {@link AEKey#fuzzyEquals}（{@link FuzzyMode#IGNORE_ALL}）。
     */
    public static boolean matchesUnlockExpected(AEKey expected, AEKey returned, NbtTearFilter filter) {
        if (expected == null || returned == null) {
            return false;
        }
        if (expected.equals(returned)) {
            return true;
        }
        if (expected instanceof AEItemKey exp && returned instanceof AEItemKey ret) {
            if (exp.getItem() == ret.getItem()) {
                return passesItemWhitelist(exp, filter);
            }
            return false;
        }
        if (expected.getClass() == returned.getClass()
                && expected.fuzzyEquals(returned, FuzzyMode.IGNORE_ALL)
                && passesItemWhitelist(expected, filter)) {
            return true;
        }
        return false;
    }

    private static boolean passesItemWhitelist(AEKey expected, NbtTearFilter filter) {
        if (filter == null || filter.itemIds().isEmpty()) {
            return true;
        }
        return filter.itemIds().contains(expected.getId());
    }

    public static NbtTearFilter fromItemStacks(List<ItemStack> stacks) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        return new NbtTearFilter(List.copyOf(ids));
    }
}
