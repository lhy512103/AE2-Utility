package com.lhy.ae2utility.network;

import java.lang.reflect.Method;

import com.lhy.ae2utility.Ae2UtilityMod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 服务端建立 EAEP pending 样板后，通知客户端设置供应器过滤词并请求 EAEP 打开供应器选择界面。
 */
public class OpenEaepProviderSelectionPacket {
    private final boolean craftingPresetOnly;
    private final String rawFilter;

    public OpenEaepProviderSelectionPacket(boolean craftingPresetOnly, String rawFilter) {
        this.craftingPresetOnly = craftingPresetOnly;
        this.rawFilter = rawFilter == null ? "" : rawFilter;
    }

    public boolean craftingPresetOnly() {
        return craftingPresetOnly;
    }

    public String rawFilter() {
        return rawFilter;
    }

    public static void encode(OpenEaepProviderSelectionPacket msg, FriendlyByteBuf buffer) {
        buffer.writeBoolean(msg.craftingPresetOnly);
        buffer.writeUtf(msg.rawFilter, 512);
    }

    public static OpenEaepProviderSelectionPacket decode(FriendlyByteBuf buffer) {
        return new OpenEaepProviderSelectionPacket(buffer.readBoolean(), buffer.readUtf(512));
    }

    public static void handle(OpenEaepProviderSelectionPacket msg) {
        if (!ModList.get().isLoaded("extendedae_plus")) {
            return;
        }

        try {
            applyEaepProviderSearchKey(msg);
            requestEaepProvidersList();
        } catch (Throwable t) {
            Ae2UtilityMod.LOGGER.error("Failed to open EAEP provider selection screen", t);
        }
    }

    private static void applyEaepProviderSearchKey(OpenEaepProviderSelectionPacket msg) throws ReflectiveOperationException {
        Class<?> configClass = Class.forName("com.extendedae_plus.util.uploadPattern.RecipeTypeNameConfig");
        if (msg.craftingPresetOnly()) {
            configClass.getMethod("presetCraftingProviderSearchKey").invoke(null);
            return;
        }

        String raw = msg.rawFilter();
        if (raw == null || raw.isBlank()) {
            configClass.getMethod("setLastProviderSearchKey", String.class).invoke(null, (String) null);
            return;
        }

        String resolved = raw;
        Method resolve = configClass.getMethod("resolveSearchKeyAlias", String.class);
        Object value = resolve.invoke(null, raw);
        if (value instanceof String s && !s.isBlank()) {
            resolved = s;
        }
        configClass.getMethod("setLastProviderSearchKey", String.class).invoke(null, resolved);
    }

    private static void requestEaepProvidersList() throws ReflectiveOperationException {
        Class<?> networkClass = Class.forName("com.extendedae_plus.init.ModNetwork");
        Object channel = networkClass.getField("CHANNEL").get(null);
        if (!(channel instanceof SimpleChannel simpleChannel)) {
            return;
        }

        Class<?> packetClass = Class.forName("com.extendedae_plus.network.provider.RequestProvidersListC2SPacket");
        Object request = packetClass.getDeclaredConstructor().newInstance();
        simpleChannel.sendToServer(request);
    }
}
