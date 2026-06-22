package com.lhy.ae2utility.jei;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.minecraft.client.Minecraft;

/**
 * JEI 样板编码使用的替换开关本地状态。
 *
 * <p>这些开关只影响 JEI 编码/上传样板，不和 AE2 样板编码终端按钮同步。</p>
 */
public final class JeiPatternSubstitutionUi {
    private static boolean localSubstitute = false;
    private static boolean localSubstituteFluids = true;
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

    public static boolean isItemSubstituteOn() {
        ensureLocalStateLoaded();
        return localSubstitute;
    }

    public static boolean isFluidSubstituteOn() {
        ensureLocalStateLoaded();
        return localSubstituteFluids;
    }

    public static void setItemSubstitute(boolean value) {
        ensureLocalStateLoaded();
        localSubstitute = value;
        saveLocalState();
    }

    public static void setFluidSubstitute(boolean value) {
        ensureLocalStateLoaded();
        localSubstituteFluids = value;
        saveLocalState();
    }

    public static void toggleItemSubstitute() {
        setItemSubstitute(!isItemSubstituteOn());
    }

    public static void toggleFluidSubstitute() {
        setFluidSubstitute(!isFluidSubstituteOn());
    }
}
