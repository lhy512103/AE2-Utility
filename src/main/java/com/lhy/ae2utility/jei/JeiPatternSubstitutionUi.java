package com.lhy.ae2utility.jei;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.lhy.ae2utility.compat.WcwtCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

/**
 * JEI 替换开关的本地状态（无线/非终端场景）。
 *
 * <p>1.20.1 forge 精简版：仅保留物品/流体替换的本地持久化开关，
 * 不再绘制批量编码按钮（批量功能未迁移）。
 * 当玩家打开样板编码类菜单时，读取菜单自身的 substitute 状态。</p>
 */
public final class JeiPatternSubstitutionUi {
    public static boolean localSubstitute = false;
    public static boolean localSubstituteFluids = true;
    private static boolean localStateLoaded = false;
    private static final String CONFIG_FILE_NAME = "ae2utility-jei-substitution.properties";

    private JeiPatternSubstitutionUi() {}

    private static void ensureLocalStateLoaded() {
        if (localStateLoaded) {
            return;
        }
        localStateLoaded = true;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameDirectory == null) {
            return;
        }
        Path path = getLocalStatePath(minecraft);
        if (!Files.exists(path)) {
            return;
        }
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
            properties.load(reader);
            localSubstitute = Boolean.parseBoolean(properties.getProperty("itemSubstitute", Boolean.toString(localSubstitute)));
            localSubstituteFluids = Boolean.parseBoolean(properties.getProperty("fluidSubstitute", Boolean.toString(localSubstituteFluids)));
        } catch (Exception ignored) {
        }
    }

    private static void saveLocalState() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameDirectory == null) {
            return;
        }
        Properties properties = new Properties();
        properties.setProperty("itemSubstitute", Boolean.toString(localSubstitute));
        properties.setProperty("fluidSubstitute", Boolean.toString(localSubstituteFluids));
        Path path = getLocalStatePath(minecraft);
        try {
            Files.createDirectories(path.getParent());
            try (var writer = Files.newBufferedWriter(path)) {
                properties.store(writer, "AE2 Utility JEI substitution toggles");
            }
        } catch (Exception ignored) {
        }
    }

    private static Path getLocalStatePath(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath().resolve("config").resolve(CONFIG_FILE_NAME);
    }

    private static @Nullable Object getOpenPatternMenu() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && WcwtCompat.isPatternEncodingLikeMenu(player.containerMenu)) {
            return player.containerMenu;
        }
        return null;
    }

    public static boolean isItemSubstituteOn() {
        ensureLocalStateLoaded();
        Object menu = getOpenPatternMenu();
        if (menu != null) {
            Boolean value = firstNonNullBoolean(menu, "isSubstitute", "isPatternSubstitute");
            if (value != null) {
                return value;
            }
        }
        return localSubstitute;
    }

    public static boolean isFluidSubstituteOn() {
        ensureLocalStateLoaded();
        Object menu = getOpenPatternMenu();
        if (menu != null) {
            Boolean value = firstNonNullBoolean(menu, "isSubstituteFluids", "isPatternFluidSubstitute");
            if (value != null) {
                return value;
            }
        }
        return localSubstituteFluids;
    }

    private static @Nullable Boolean invokeBoolean(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value instanceof Boolean b ? b : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable Boolean firstNonNullBoolean(Object target, String... methodNames) {
        for (String name : methodNames) {
            Boolean v = invokeBoolean(target, name);
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
