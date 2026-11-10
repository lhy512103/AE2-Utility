package com.lhy.ae2utility.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** Routes client feedback to the original chat or action-bar destination. */
public final class ClientMessages {
    private ClientMessages() {}

    public static void show(LocalPlayer player, Component message, boolean overlay) {
        if (overlay) {
            player.sendOverlayMessage(message);
        } else {
            player.sendSystemMessage(message);
        }
    }
}
