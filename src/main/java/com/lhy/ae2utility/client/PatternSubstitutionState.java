package com.lhy.ae2utility.client;

import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;
import com.lhy.ae2utility.compat.WcwtCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Client menu/preferences adapter, shared by viewers without loading either viewer's UI. */
public final class PatternSubstitutionState {
    private static boolean localSubstitute;
    private static boolean localSubstituteFluids = true;
    private static boolean localStateLoaded;
    private static final String CONFIG_FILE_NAME = "ae2utility-jei-substitution.properties";

    private PatternSubstitutionState() {}

    public static PatternSubstitutionRules snapshot() {
        ensureLocalStateLoaded();
        Object menu = getOpenPatternMenu();
        Boolean items = menu == null ? null : firstNonNullBoolean(menu, "isSubstitute", "isPatternSubstitute");
        Boolean fluids = menu == null ? null : firstNonNullBoolean(menu, "isSubstituteFluids", "isPatternFluidSubstitute");
        return new PatternSubstitutionRules(items == null ? localSubstitute : items,
                fluids == null ? localSubstituteFluids : fluids);
    }

    private static void ensureLocalStateLoaded() {
        if (localStateLoaded) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameDirectory == null) {
            return;
        }
        localStateLoaded = true;
        try {
            PatternSubstitutionRules rules = PatternSubstitutionRules.load(getLocalStatePath(minecraft),
                    new PatternSubstitutionRules(localSubstitute, localSubstituteFluids));
            localSubstitute = rules.items();
            localSubstituteFluids = rules.fluids();
        } catch (java.io.IOException | IllegalArgumentException ignored) {
            // Missing or invalid preferences retain the existing local defaults.
        }
    }

    private static void saveLocalState() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameDirectory == null) {
            return;
        }
        try {
            new PatternSubstitutionRules(localSubstitute, localSubstituteFluids).save(getLocalStatePath(minecraft));
        } catch (java.io.IOException ignored) {
            // A read-only config directory must not prevent encoding.
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

    public static boolean isSubstitutionContextActive() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        if (WcwtCompat.isPatternEncodingLikeMenu(player.containerMenu)) {
            return true;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            String itemName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getInventory().getItem(i).getItem()).toString();
            if (itemName.contains("pattern_encoding") || itemName.contains("universal_terminal")) {
                return true;
            }
        }
        var curios = com.lhy.ae2utility.service.CuriosWirelessTerminalLookup.items(player);
        if (curios != null) {
            for (int slot = 0; slot < curios.getSlots(); slot++) {
                String itemName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(curios.getStackInSlot(slot).getItem()).toString();
                if (itemName.contains("pattern_encoding") || itemName.contains("universal_terminal")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isItemSubstituteOn() {
        ensureLocalStateLoaded();
        Object menu = getOpenPatternMenu();
        if (menu != null) {
            Boolean value = firstNonNullBoolean(menu, "isSubstitute", "isPatternSubstitute");
            if (value != null) {
                return value.booleanValue();
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
                return value.booleanValue();
            }
        }
        return localSubstituteFluids;
    }

    public static void toggleItemSubstitute() {
        ensureLocalStateLoaded();
        Object menu = getOpenPatternMenu();
        if (menu != null) {
            boolean next = !isItemSubstituteOn();
            boolean applied = invokeVoidBoolean(menu, "setSubstitute", next)
                    || invokeVoidBoolean(menu, "setPatternSubstitute", next);
            if (applied) {
                localSubstitute = isItemSubstituteOn();
            } else {
                localSubstitute = next;
            }
        } else {
            localSubstitute = !localSubstitute;
        }
        saveLocalState();
    }

    public static void toggleFluidSubstitute() {
        ensureLocalStateLoaded();
        Object menu = getOpenPatternMenu();
        if (menu != null) {
            boolean next = !isFluidSubstituteOn();
            boolean applied = invokeVoidBoolean(menu, "setSubstituteFluids", next)
                    || invokeVoidBoolean(menu, "setPatternFluidSubstitute", next);
            if (applied) {
                localSubstituteFluids = isFluidSubstituteOn();
            } else {
                localSubstituteFluids = next;
            }
        } else {
            localSubstituteFluids = !localSubstituteFluids;
        }
        saveLocalState();
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

    /** @return 是否成功调用到对应 setter（AE2 样板终端与 WCWT 方法名不同） */
    private static boolean invokeVoidBoolean(Object target, String methodName, boolean value) {
        try {
            target.getClass().getMethod(methodName, boolean.class).invoke(target, value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

}
