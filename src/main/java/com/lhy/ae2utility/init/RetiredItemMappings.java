package com.lhy.ae2utility.init;

import com.lhy.ae2utility.Ae2UtilityMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.MissingMappingsEvent;

/** Preserve old-world inventories without keeping the removed finder item registered. */
@Mod.EventBusSubscriber(modid = Ae2UtilityMod.MOD_ID)
public final class RetiredItemMappings {
    private RetiredItemMappings() {}

    public static boolean isRetiredFinder(String registryId) {
        return "ae2utility:recipe_finder".equals(registryId);
    }

    @SubscribeEvent
    public static void missingMappings(MissingMappingsEvent event) {
        for (var mapping : event.getMappings(Registries.ITEM, Ae2UtilityMod.MOD_ID)) {
            if (isRetiredFinder(mapping.getKey().toString())) {
                mapping.remap(Items.COMPASS);
                Ae2UtilityMod.LOGGER.info("Migrated retired recipe finder item to minecraft:compass");
            }
        }
    }
}
