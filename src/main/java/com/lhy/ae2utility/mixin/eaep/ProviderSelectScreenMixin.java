package com.lhy.ae2utility.mixin.eaep;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.lhy.ae2utility.client.EaepProviderReuse;

@Mixin(targets = "com.extendedae_plus.client.screen.ProviderSelectScreen", remap = false)
public abstract class ProviderSelectScreenMixin {
    @Shadow
    @Final
    private List<String> fNames;

    @Shadow
    private String query;

    @Inject(method = "onChoose(IZ)V", at = @At("HEAD"), remap = false)
    private void ae2utility$rememberChoice(int idx, boolean showStatusMessage, CallbackInfo ci) {
        if (idx >= 0 && idx < fNames.size()) {
            EaepProviderReuse.record(query, fNames.get(idx));
        }
    }

    // 本批次记住的同名供应器已不在列表（如已装满）时，不让 EAEP 把样板自动传给另一个异名供应器。
    @Inject(method = "tryAutoUploadIfUniqueMatch", at = @At("HEAD"), cancellable = true, remap = false)
    private void ae2utility$suppressAutoUploadWhenRememberedAbsent(CallbackInfo ci) {
        if (EaepProviderReuse.rememberedButAbsent(query, fNames)) {
            ci.cancel();
        }
    }
}
