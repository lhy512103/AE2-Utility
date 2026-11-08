package com.lhy.ae2utility.debug;

import com.lhy.ae2utility.Ae2UtilityMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/** Opt-in development check: force lazy integration targets through the Mixin transformer, then exit. */
@Mod.EventBusSubscriber(modid = Ae2UtilityMod.MOD_ID, value = Dist.CLIENT)
public final class MigrationSmokeTest {
    private static boolean done;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("ae2utility.migrationSmokeTest") || done
                || event.phase != TickEvent.Phase.END || !(Minecraft.getInstance().screen instanceof TitleScreen)) return;
        done = true;
        String[][] targets = {
            {"ae2", "appeng.crafting.CraftingCalculation"},
            {"ae2", "appeng.crafting.CraftingTreeNode"},
            {"ae2", "appeng.crafting.CraftingTreeProcess"},
            {"ae2", "appeng.crafting.execution.CraftingCpuLogic"},
            {"ae2", "appeng.crafting.execution.CraftingCpuHelper"},
            {"ae2", "appeng.crafting.pattern.AEProcessingPattern"},
            {"ae2", "appeng.crafting.pattern.AEProcessingPattern$Input"},
            {"ae2", "appeng.crafting.pattern.AECraftingPattern$Input"},
            {"ae2", "appeng.crafting.pattern.AESmithingTablePattern$Input"},
            {"ae2", "appeng.crafting.pattern.AEStonecuttingPattern$Input"},
            {"ae2", "appeng.me.service.helpers.NetworkCraftingProviders"},
            {"ae2", "appeng.client.gui.me.items.PatternEncodingTermScreen"},
            {"extendedae_plus", "com.extendedae_plus.client.screen.ProviderSelectScreen"},
            {"extendedae_plus", "com.extendedae_plus.util.uploadPattern.ProviderUploadUtil"},
            {"advanced_ae", "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic"},
            {"advanced_ae", "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic"},
            {"advanced_ae", "net.pedroksl.advanced_ae.common.patterns.AdvProcessingPattern"},
            {"neoecoae", "cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic"},
            {"neoecoae", "cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic"},
            {"ae2cs", "io.github.lounode.ae2cs.common.me.crafting.ResonatingPatternDetails"},
            {"ae2cs", "io.github.lounode.ae2cs.common.me.crafting.ResonatingPatternDetails$Input"},
            {"ae2cs", "io.github.lounode.ae2cs.common.me.logic.ResonatingPatternProviderLogic"},
            {"ae2cs", "io.github.lounode.ae2cs.common.me.logic.MeteoritePatternProviderLogic"},
            {"ae2lt", "com.moakiee.ae2lt.logic.provider.OverloadedPatternProviderLogic"},
            {"ae2lt", "com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity"},
            {"ae2lt", "com.moakiee.ae2lt.block.OverloadedPatternProviderBlock"},
            {"jei", "mezz.jei.gui.recipes.RecipeTransferButtonController"},
            {"jei", "mezz.jei.library.gui.recipes.RecipeLayout"},
            {"jei", "mezz.jei.library.gui.ingredients.RecipeSlot"},
            {"jei", "mezz.jei.gui.recipes.RecipeOptionButtons"},
            {"jei", "mezz.jei.gui.recipes.RecipesGui"}
        };
        int checked = 0;
        for (String[] target : targets) {
            if (!ModList.get().isLoaded(target[0])) continue;
            try {
                Class.forName(target[1], false, MigrationSmokeTest.class.getClassLoader());
                checked++;
                Ae2UtilityMod.LOGGER.info("Migration smoke: loaded {}", target[1]);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Missing migration target " + target[1], e);
            }
        }
        if (ModList.get().isLoaded("jei")) {
            var profiles = com.lhy.ae2utility.machine.MachineTransferProfiles.all();
            if (profiles.size() < 6) throw new IllegalStateException("Missing built-in machine profiles");
            Ae2UtilityMod.LOGGER.info("Migration smoke: machine profiles {}", profiles.stream().map(p -> p.id()).toList());
        }
        Ae2UtilityMod.LOGGER.info("MIGRATION_SMOKE_PASS: title screen reached, {} integration targets verified", checked);
        Minecraft.getInstance().stop();
    }
}
