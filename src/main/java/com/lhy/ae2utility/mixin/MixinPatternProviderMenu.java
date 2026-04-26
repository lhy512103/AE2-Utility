package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;

import com.lhy.ae2utility.integration.ae2.NbtTearLogicAccess;
import com.lhy.ae2utility.menu.PatternProviderTearSlot;


/**
 * 在纯 AE2 环境下（无 EAEP 接管升级库存时），向 PatternProviderMenu 中注入一个专属撕裂卡槽。
 */
@Mixin(value = PatternProviderMenu.class, priority = 3000)
public abstract class MixinPatternProviderMenu {

    @Shadow
    @Final
    protected PatternProviderLogic logic;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ae2utility$addTearSlot(MenuType<?> menuType, int id, Inventory playerInventory, PatternProviderLogicHost host,
            CallbackInfo ci) {
        if (net.neoforged.fml.ModList.get().isLoaded("extendedae_plus")) {
            return;
        }
        var handler = ((NbtTearLogicAccess) logic).ae2utility$getTearHandler();
        // 纯 AE2 环境时，由 MixinAEBaseScreen 的回退逻辑自行绘制和定位。
        ((AEBaseMenuInvoker) (Object) this).ae2utility$addSlot(
                new PatternProviderTearSlot(handler, 0, 181, 52),
                SlotSemantics.UPGRADE);
    }
}
