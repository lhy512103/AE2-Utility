package com.lhy.ae2utility.card;

import net.minecraft.network.chat.Component;

/** Redstone card operating mode. */
public enum RedstoneSignalCardMode {
    ORDER("order"), CRAFT("craft"), UNTIL_RECIPE_COMPLETE("until_recipe");

    private final String name;
    RedstoneSignalCardMode(String name) { this.name = name; }
    public String getSerializedName() { return name; }
    public Component getDisplayName() { return Component.translatable("item.ae2utility.redstone_signal_card.mode." + name); }
    public static RedstoneSignalCardMode byName(String value) {
        for (RedstoneSignalCardMode mode : values()) if (mode.name.equals(value)) return mode;
        return ORDER;
    }
}
