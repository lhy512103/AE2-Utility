package com.lhy.ae2utility.init;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.card.NbtTearFilter;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents REG = DeferredRegister.createDataComponents(Ae2UtilityMod.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<NbtTearFilter>> NBT_TEAR_FILTER = REG.registerComponentType(
            "nbt_tear_filter",
            builder -> builder.persistent(NbtTearFilter.CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodecWithRegistries(NbtTearFilter.CODEC)));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> PATTERN_PROVIDER_SEARCH_KEY = REG.registerComponentType(
            "pattern_provider_search_key",
            builder -> builder.persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    private ModDataComponents() {
    }
}
