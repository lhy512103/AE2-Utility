package com.lhy.ae2utility.card;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * {@link #ORDER}：下单派发完成后信号；
 * {@link #CRAFT}：返还进网匹配；
 * {@link #UNTIL_RECIPE_COMPLETE}：下单派发完成后拉高，直到本次配方产物返还进网完毕再拉低。
 */
public enum RedstoneSignalCardMode implements StringRepresentable {
    ORDER("order"),
    CRAFT("craft"),
    UNTIL_RECIPE_COMPLETE("until_recipe");

    public static final Codec<RedstoneSignalCardMode> CODEC =
            Codec.STRING.comapFlatMap(RedstoneSignalCardMode::bySerializedNameStrict,
                    RedstoneSignalCardMode::getSerializedName);

    private final String serializedName;

    RedstoneSignalCardMode(String serializedName) {
        this.serializedName = serializedName;
    }

    private static DataResult<RedstoneSignalCardMode> bySerializedNameStrict(String s) {
        for (var v : values()) {
            if (v.serializedName.equals(s)) {
                return DataResult.success(v);
            }
        }
        return DataResult.error(() -> "Unknown redstone signal card mode: " + s);
    }

    public Component getDisplayName() {
        return Component.translatable("item.ae2utility.redstone_signal_card.mode." + serializedName);
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
