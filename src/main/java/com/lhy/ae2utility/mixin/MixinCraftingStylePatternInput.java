package com.lhy.ae2utility.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.lhy.ae2utility.card.NbtTearPatternMatchHelper;

/**
 * 统一放宽合成/锻造/切石/共振等 pattern Input 在 {@code isValid} 中对模板 NBT 的严格匹配。
 * 仅当对应样板供应器装有 NBT 撕裂卡时才放宽。
 */
@Mixin(targets = {
        "appeng/crafting/pattern/AECraftingPattern$Input",
        "appeng/crafting/pattern/AESmithingTablePattern$Input",
        "appeng/crafting/pattern/AEStonecuttingPattern$Input",
        "io.github.lounode.ae2cs.common.me.crafting.ResonatingPatternDetails$Input"
}, remap = false)
public class MixinCraftingStylePatternInput {

    @Redirect(method = "isValid",
            at = @At(value = "INVOKE", target = "Lappeng/api/stacks/AEKey;matches(Lappeng/api/stacks/GenericStack;)Z"))
    private boolean ae2utility$redirectMatches(AEKey input, GenericStack templateStack) {
        return NbtTearPatternMatchHelper.matchesCraftingPatternInput(input, templateStack);
    }
}
