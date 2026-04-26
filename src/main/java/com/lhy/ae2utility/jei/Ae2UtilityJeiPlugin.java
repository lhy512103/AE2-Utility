package com.lhy.ae2utility.jei;

import java.lang.reflect.Field;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;

import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.me.items.CraftingTermMenu;
import appeng.menu.me.items.WirelessCraftingTermMenu;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;

import com.lhy.ae2utility.Ae2UtilityMod;
import com.lhy.ae2utility.client.NbtTearCardScreen;
import com.lhy.ae2utility.machine.MachineTransferProfile;
import com.lhy.ae2utility.machine.MachineTransferProfiles;

@JeiPlugin
public class Ae2UtilityJeiPlugin implements IModPlugin {
    private static final String AE2_WTLIB_WCT_MENU = "de.mari_023.ae2wtlib.wct.WCTMenu";

    private static mezz.jei.api.runtime.IJeiRuntime jeiRuntime;

    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime jeiRuntime) {
        Ae2UtilityJeiPlugin.jeiRuntime = jeiRuntime;
    }

    public static mezz.jei.api.runtime.IJeiRuntime getJeiRuntime() {
        return jeiRuntime;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Ae2UtilityMod.MOD_ID, "jei_plugin");
    }

    @Override
    public void registerAdvanced(mezz.jei.api.registration.IAdvancedRegistration registration) {
        registration.addRecipeButtonFactory(new EncodePatternButtonFactory());
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(NbtTearCardScreen.class, new NbtTearCardGhostHandler());
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        var helper = registration.getTransferHelper();

        registerHandler(registration, new Ae2TerminalRecipeTransferHandler<>(MEStorageMenu.class, MEStorageMenu.TYPE, helper));
        registerHandler(registration,
                new Ae2TerminalRecipeTransferHandler<>(MEStorageMenu.class, MEStorageMenu.WIRELESS_TYPE, helper));
        registerHandler(registration,
                new Ae2TerminalRecipeTransferHandler<>(CraftingTermMenu.class, CraftingTermMenu.TYPE, helper));
        registerHandler(registration,
                new Ae2TerminalRecipeTransferHandler<>(WirelessCraftingTermMenu.class, WirelessCraftingTermMenu.TYPE, helper));

        registerOptionalHandler(registration, helper, AE2_WTLIB_WCT_MENU);
        registerMachineHandlers(registration, helper);
    }

    private static void registerHandler(IRecipeTransferRegistration registration,
            Ae2TerminalRecipeTransferHandler<? extends MEStorageMenu> handler) {
        registration.addUniversalRecipeTransferHandler(handler);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void registerOptionalHandler(IRecipeTransferRegistration registration,
            IRecipeTransferHandlerHelper helper, String menuClassName) {
        try {
            Class<?> rawMenuClass = Class.forName(menuClassName);
            if (!MEStorageMenu.class.isAssignableFrom(rawMenuClass)) {
                return;
            }

            Field typeField = rawMenuClass.getField("TYPE");
            Object rawMenuType = typeField.get(null);
            if (!(rawMenuType instanceof MenuType<?> menuType)) {
                return;
            }

            Class<? extends MEStorageMenu> menuClass = (Class<? extends MEStorageMenu>) rawMenuClass;
            registration.addUniversalRecipeTransferHandler(
                    new Ae2TerminalRecipeTransferHandler(menuClass, (MenuType) menuType, helper));
        } catch (ClassNotFoundException ignored) {
            // Optional integration, safe to skip when AE2WTLib is not installed.
        } catch (ReflectiveOperationException ignored) {
            // Ignore if the optional mod changes its menu API.
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void registerMachineHandlers(IRecipeTransferRegistration registration,
            IRecipeTransferHandlerHelper helper) {
        for (MachineTransferProfile profile : MachineTransferProfiles.all()) {
            var handler = new MachineRecipeTransferHandler(profile, helper);
            registration.addRecipeTransferHandler(handler, profile.recipeType());
        }
    }
}
