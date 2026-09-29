package com.lhy.ae2utility.init;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.menu.RecipeFinderMenu;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> REG = DeferredRegister.create(ForgeRegistries.MENU_TYPES,
            Ae2UtilityMod.MOD_ID);

    public static final RegistryObject<MenuType<RecipeFinderMenu>> RECIPE_FINDER = REG.register("recipe_finder",
            () -> IForgeMenuType.create(RecipeFinderMenu::fromNetwork));

    private ModMenus() {
    }
}
