package com.lhy.ae2utility.mixin;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import net.minecraftforge.fml.loading.LoadingModList;

/**
 * Optional-mod mixins. Must not {@link Class#forName} mixin targets: that loads them
 * before Mixin can apply and crashes with MixinTargetAlreadyLoadedException.
 */
public final class Ae2UtilityMixinPlugin implements IMixinConfigPlugin {
    private final java.util.function.Predicate<String> modPresent;

    public Ae2UtilityMixinPlugin() {
        this(Ae2UtilityMixinPlugin::isModPresent);
    }

    Ae2UtilityMixinPlugin(java.util.function.Predicate<String> modPresent) {
        this.modPresent = java.util.Objects.requireNonNull(modPresent, "modPresent");
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String target = targetClassName.replace('/', '.');
        if (target.startsWith("mezz.jei.") && !modPresent.test("jei")) return false;
        if (target.startsWith("com.extendedae_plus.") && !modPresent.test("extendedae_plus")) return false;
        if (target.startsWith("net.pedroksl.advanced_ae.") && !modPresent.test("advanced_ae")) return false;
        if (target.startsWith("cn.dancingsnow.neoecoae.") && !modPresent.test("neoecoae")) return false;
        if (target.startsWith("io.github.lounode.ae2cs.") && !modPresent.test("ae2cs")) return false;
        if (target.startsWith("com.moakiee.ae2lt.") && !modPresent.test("ae2lt")) return false;
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        return switch (simple) {
            case "MixinAdvPatternProviderLogic", "MixinAdvStylePatternProviderBlock" -> modPresent.test("advanced_ae");
            case "MixinOverloadedPatternProviderLogic", "MixinOverloadedPatternProviderBlockEntity",
                    "MixinOverloadedPatternProviderBlock" -> modPresent.test("ae2lt");
            case "MixinMeteoritePatternProviderLogic", "MixinResonatingPatternProviderLogic",
                    "MixinResonatingPatternDetailsInput" -> modPresent.test("ae2cs");
            default -> true;
        };
    }

    private static boolean isModPresent(String modId) {
        try {
            return LoadingModList.get() != null && LoadingModList.get().getModFileById(modId) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
